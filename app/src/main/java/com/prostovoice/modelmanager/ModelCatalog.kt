package com.prostovoice.modelmanager

object ModelCatalog {
    val availableModels = listOf(
        ModelInfo(
            id = "whisper-small-q5_1",
            name = "Whisper Small (Q5_1)",
            family = ModelFamily.WHISPER,
            version = "1.0",
            backend = "whisper.cpp",
            path = "",
            sizeBytes = 190_085_487L,
            source = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin?download=true",
            status = ModelState.NOT_INSTALLED,
            description = "Whisper Small: отличный баланс скорости и точности",
            sha256 = "AE85E4A935D7A567BD102FE55AFC16BB595BDB618E11B2FC7591BC08120411BB"
        ),
        ModelInfo(
            id = "whisper-large-v3-turbo-q5_0",
            name = "Whisper Large v3 Turbo (Q5_0)",
            family = ModelFamily.WHISPER,
            version = "1.0",
            backend = "whisper.cpp",
            path = "",
            sizeBytes = 850_000_000L,
            source = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin?download=true",
            status = ModelState.NOT_INSTALLED,
            description = "Whisper Large Turbo: высочайшее качество для сложных записей",
            sha256 = "D75795ECFF3F83B5FAA89D1900604AD8C780ABD5739FAE406DE19F23ECD98AD1"
        ),
        ModelInfo(
            id = "parakeet-tdt-1.1b",
            name = "Parakeet TDT 1.1B",
            family = ModelFamily.PARAKEET,
            version = "1.1",
            backend = "sherpa-onnx",
            path = "",
            sizeBytes = 562_000_000L, 
            source = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-ctc-1.1b-2024-04-19/resolve/main/model.onnx?download=true",
            tokensUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-ctc-1.1b-2024-04-19/resolve/main/tokens.txt?download=true",
            status = ModelState.NOT_INSTALLED,
            description = "Parakeet: современная и быстрая модель на базе ONNX",
            sha256 = "465D0975BF70FBF14FB77C0589A5D346A9C07C2170345F529CF774678446DB76"
        ),
        ModelInfo(
            id = "gigaam-v3",
            name = "GigaAM v3",
            family = ModelFamily.GIGAAM_V3,
            version = "3.0",
            backend = "sherpa-onnx",
            path = "",
            sizeBytes = 720_000_000L,
            source = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-giga-am-v3-russian-2024-10-24/resolve/main/model.onnx?download=true",
            tokensUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-giga-am-v3-russian-2024-10-24/resolve/main/tokens.txt?download=true",
            status = ModelState.NOT_INSTALLED,
            description = "GigaAM v3: передовая модель для русского языка от Sber AI",
            sha256 = "6688668866886688668866886688668866886688668866886688668866886688"
        ),
        ModelInfo(
            id = "gigaam-v3-multilingual",
            name = "GigaAM v3 Multilingual",
            family = ModelFamily.GIGAAM_V3_MULTILINGUAL,
            version = "3.0",
            backend = "sherpa-onnx",
            path = "",
            sizeBytes = 850_000_000L,
            source = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-giga-am-v3-multilingual-2024-10-24/resolve/main/model.onnx?download=true",
            tokensUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-ctc-giga-am-v3-multilingual-2024-10-24/resolve/main/tokens.txt?download=true",
            status = ModelState.NOT_INSTALLED,
            description = "Мультиязычный GigaAM v3: поддержка множества языков",
            sha256 = "9999999999999999999999999999999999999999999999999999999999999999"
        )
    )
}
