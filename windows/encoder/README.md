# Encoder boundary

Media Foundation selects an asynchronous, D3D11-aware hardware H.264 MFT on the
capture adapter. Baseline excludes B slices; the encoder requests low latency,
CBR and a half-second GOP. It reports unsupported optional controls and never
silently selects a software encoder.

NeedInput events grant input credits. HaveOutput events authorize ProcessOutput.
At most three samples are in flight, each retaining its own NV12 surface. Output
timestamps are checked against submitted captures. Flush and shutdown release
owned samples before the video runtime is destroyed.
