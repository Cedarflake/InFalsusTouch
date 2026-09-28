#include "windows/encoder/hardware-encoder.h"

#include <mferror.h>

#include <stdexcept>

#include "protocol/cpp/video-packet.h"

namespace ift {

bool HardwareEncoder::canAccept() const { return credits_ > 0 && timestamps_.size() < 3; }

void HardwareEncoder::submit(ID3D11Texture2D* texture, std::uint64_t timestamp) {
  if (!canAccept()) throw std::logic_error("Encoder input submitted without credit");
  winrt::com_ptr<IMFMediaBuffer> buffer;
  winrt::check_hresult(MFCreateDXGISurfaceBuffer(__uuidof(ID3D11Texture2D), texture, 0, FALSE, buffer.put()));
  winrt::com_ptr<IMFSample> sample;
  winrt::check_hresult(MFCreateSample(sample.put()));
  winrt::check_hresult(sample->AddBuffer(buffer.get()));
  const auto time = static_cast<LONGLONG>(timestamp / 100);
  winrt::check_hresult(sample->SetSampleTime(time));
  winrt::check_hresult(sample->SetSampleDuration(10'000'000 / options_.fps));
  if (first_) winrt::check_hresult(sample->SetUINT32(MFSampleExtension_Discontinuity, TRUE));
  if (!timestamps_.emplace(time, timestamp).second) throw std::runtime_error("Duplicate capture timestamp");
  winrt::check_hresult(transform_->ProcessInput(inputId_, sample.get(), 0));
  --credits_;
  first_ = false;
}

std::vector<EncodedFrame> HardwareEncoder::poll() {
  std::vector<EncodedFrame> output;
  for (unsigned count = 0; count < 32; ++count) {
    winrt::com_ptr<IMFMediaEvent> event;
    const auto result = events_->GetEvent(MF_EVENT_FLAG_NO_WAIT, event.put());
    if (result == MF_E_NO_EVENTS_AVAILABLE) break;
    winrt::check_hresult(result);
    HRESULT status = S_OK;
    winrt::check_hresult(event->GetStatus(&status));
    winrt::check_hresult(status);
    MediaEventType type{};
    winrt::check_hresult(event->GetType(&type));
    if (type == METransformNeedInput) {
      if (credits_ >= 32) throw std::runtime_error("Encoder input credit overflow");
      ++credits_;
    } else if (type == METransformHaveOutput) {
      output.push_back(readOutput());
    }
  }
  return output;
}

EncodedFrame HardwareEncoder::readOutput() {
  MFT_OUTPUT_STREAM_INFO info{};
  winrt::check_hresult(transform_->GetOutputStreamInfo(outputId_, &info));
  winrt::com_ptr<IMFSample> sample;
  if (!(info.dwFlags & MFT_OUTPUT_STREAM_PROVIDES_SAMPLES)) {
    if (info.cbSize > video::maxPayloadSize) throw std::runtime_error("Encoder output allocation exceeds limit");
    winrt::check_hresult(MFCreateSample(sample.put()));
    winrt::com_ptr<IMFMediaBuffer> buffer;
    winrt::check_hresult(MFCreateAlignedMemoryBuffer(info.cbSize,
      info.cbAlignment > 0 ? info.cbAlignment - 1 : 0, buffer.put()));
    winrt::check_hresult(sample->AddBuffer(buffer.get()));
  }
  MFT_OUTPUT_DATA_BUFFER output{};
  output.dwStreamID = outputId_;
  output.pSample = sample.get();
  DWORD status = 0;
  const auto result = transform_->ProcessOutput(0, 1, &output, &status);
  if (output.pEvents) output.pEvents->Release();
  if (!sample && output.pSample) sample.attach(output.pSample);
  winrt::check_hresult(result);
  if (!sample) throw std::runtime_error("Encoder produced an empty output sample");
  LONGLONG timestamp = 0;
  winrt::check_hresult(sample->GetSampleTime(&timestamp));
  const auto found = timestamps_.find(timestamp);
  if (found == timestamps_.end()) throw std::runtime_error("Encoder output timestamp has no capture sample");
  EncodedFrame frame;
  frame.captureTimestamp = found->second;
  frame.encodeTimestamp = performanceNanoseconds();
  timestamps_.erase(found);
  winrt::com_ptr<IMFMediaBuffer> buffer;
  winrt::check_hresult(sample->ConvertToContiguousBuffer(buffer.put()));
  BYTE* bytes = nullptr;
  DWORD size = 0;
  winrt::check_hresult(buffer->Lock(&bytes, nullptr, &size));
  try {
    if (size == 0 || size > video::maxPayloadSize) throw std::runtime_error("Invalid encoded frame size");
    frame.bytes.assign(bytes, bytes + size);
  } catch (...) {
    buffer->Unlock();
    throw;
  }
  winrt::check_hresult(buffer->Unlock());
  frame.bytes = video::toAnnexB(frame.bytes);
  frame.keyFrame = video::hasNal(frame.bytes, 5);
  return frame;
}

std::vector<std::uint8_t> HardwareEncoder::parameterSets() const {
  winrt::com_ptr<IMFMediaType> type;
  winrt::check_hresult(transform_->GetOutputCurrentType(outputId_, type.put()));
  UINT32 size = 0;
  if (FAILED(type->GetBlobSize(MF_MT_MPEG_SEQUENCE_HEADER, &size)) || size == 0) return {};
  if (size > 65536) throw std::runtime_error("Encoder parameter sets exceed limit");
  std::vector<std::uint8_t> bytes(size);
  winrt::check_hresult(type->GetBlob(MF_MT_MPEG_SEQUENCE_HEADER, bytes.data(), size, nullptr));
  return video::extractParameterSets(video::toAnnexB(bytes));
}

}
