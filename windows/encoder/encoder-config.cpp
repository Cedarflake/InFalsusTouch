#include "windows/encoder/hardware-encoder.h"

#include <codecapi.h>
#include <icodecapi.h>
#include <mferror.h>

#include <iostream>
#include <stdexcept>

namespace ift {
namespace {

void codecOption(ICodecAPI* codec, const GUID& key, ULONG value, const char* label, bool boolean = false) {
  VARIANT option{};
  option.vt = static_cast<VARTYPE>(boolean ? VT_BOOL : VT_UI4);
  if (boolean) option.boolVal = value ? VARIANT_TRUE : VARIANT_FALSE;
  else option.ulVal = value;
  if (FAILED(codec->SetValue(&key, &option))) {
    std::cerr << "Video encoder did not accept optional setting: " << label << '\n';
  }
}

winrt::com_ptr<IMFMediaType> mediaType(const VideoOptions& options, const GUID& subtype) {
  winrt::com_ptr<IMFMediaType> type;
  winrt::check_hresult(MFCreateMediaType(type.put()));
  winrt::check_hresult(type->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video));
  winrt::check_hresult(type->SetGUID(MF_MT_SUBTYPE, subtype));
  winrt::check_hresult(type->SetUINT32(MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive));
  winrt::check_hresult(MFSetAttributeSize(type.get(), MF_MT_FRAME_SIZE, options.width, options.height));
  winrt::check_hresult(MFSetAttributeRatio(type.get(), MF_MT_FRAME_RATE, options.fps, 1));
  winrt::check_hresult(MFSetAttributeRatio(type.get(), MF_MT_PIXEL_ASPECT_RATIO, 1, 1));
  winrt::check_hresult(type->SetUINT32(MF_MT_VIDEO_PRIMARIES, MFVideoPrimaries_BT709));
  winrt::check_hresult(type->SetUINT32(MF_MT_YUV_MATRIX, MFVideoTransferMatrix_BT709));
  winrt::check_hresult(type->SetUINT32(MF_MT_VIDEO_NOMINAL_RANGE, MFNominalRange_16_235));
  return type;
}

}

HardwareEncoder::HardwareEncoder(const GraphicsDevice& graphics, VideoOptions options)
  : options_(options), activation_(findHardwareEncoder(graphics)) {
  try {
    configure(graphics);
  } catch (...) {
    activation_->ShutdownObject();
    throw;
  }
}

void HardwareEncoder::configure(const GraphicsDevice& graphics) {
  winrt::check_hresult(activation_->ActivateObject(IID_PPV_ARGS(transform_.put())));
  winrt::com_ptr<IMFAttributes> attributes;
  winrt::check_hresult(transform_->GetAttributes(attributes.put()));
  UINT32 async = 0;
  UINT32 aware = 0;
  winrt::check_hresult(attributes->GetUINT32(MF_TRANSFORM_ASYNC, &async));
  winrt::check_hresult(attributes->GetUINT32(MF_SA_D3D11_AWARE, &aware));
  if (!async || !aware) throw std::runtime_error("H.264 encoder needs async D3D11 surface input");
  winrt::check_hresult(attributes->SetUINT32(MF_TRANSFORM_ASYNC_UNLOCK, TRUE));
  winrt::check_hresult(attributes->SetUINT32(MF_LOW_LATENCY, TRUE));
  events_ = transform_.as<IMFMediaEventGenerator>();
  DWORD inputs = 0;
  DWORD outputs = 0;
  winrt::check_hresult(transform_->GetStreamCount(&inputs, &outputs));
  if (inputs != 1 || outputs != 1) throw std::runtime_error("Unexpected hardware encoder stream count");
  const auto ids = transform_->GetStreamIDs(1, &inputId_, 1, &outputId_);
  if (ids != E_NOTIMPL) winrt::check_hresult(ids);
  UINT token = 0;
  winrt::check_hresult(MFCreateDXGIDeviceManager(&token, manager_.put()));
  winrt::check_hresult(manager_->ResetDevice(graphics.device.get(), token));
  winrt::check_hresult(transform_->ProcessMessage(MFT_MESSAGE_SET_D3D_MANAGER,
    reinterpret_cast<ULONG_PTR>(manager_.get())));
  const auto codec = transform_.as<ICodecAPI>();
  codecOption(codec.get(), CODECAPI_AVLowLatencyMode, TRUE, "low latency", true);
  codecOption(codec.get(), CODECAPI_AVEncCommonRateControlMode, eAVEncCommonRateControlMode_CBR, "CBR");
  codecOption(codec.get(), CODECAPI_AVEncCommonMeanBitRate, options_.bitrate, "bitrate");
  codecOption(codec.get(), CODECAPI_AVEncMPVGOPSize, options_.fps / 2, "half-second GOP");
  codecOption(codec.get(), CODECAPI_AVEncMPVDefaultBPictureCount, 0, "zero B frames");

  const auto output = mediaType(options_, MFVideoFormat_H264);
  winrt::check_hresult(output->SetUINT32(MF_MT_AVG_BITRATE, options_.bitrate));
  // Baseline forbids B slices even when a vendor ignores the optional B-count control.
  winrt::check_hresult(output->SetUINT32(MF_MT_MPEG2_PROFILE, eAVEncH264VProfile_Base));
  winrt::check_hresult(transform_->SetOutputType(outputId_, output.get(), 0));
  const auto input = mediaType(options_, MFVideoFormat_NV12);
  winrt::check_hresult(transform_->SetInputType(inputId_, input.get(), 0));
  winrt::check_hresult(transform_->ProcessMessage(MFT_MESSAGE_NOTIFY_BEGIN_STREAMING, 0));
  winrt::check_hresult(transform_->ProcessMessage(MFT_MESSAGE_NOTIFY_START_OF_STREAM, 0));
}

HardwareEncoder::~HardwareEncoder() {
  if (transform_) {
    transform_->ProcessMessage(MFT_MESSAGE_COMMAND_FLUSH, 0);
    transform_->ProcessMessage(MFT_MESSAGE_NOTIFY_END_STREAMING, 0);
  }
  if (activation_) activation_->ShutdownObject();
}

}
