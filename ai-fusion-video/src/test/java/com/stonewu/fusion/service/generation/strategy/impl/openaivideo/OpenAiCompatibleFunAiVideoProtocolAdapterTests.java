package com.stonewu.fusion.service.generation.strategy.impl.openaivideo;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.service.ai.model.AiModelMetadata;
import com.stonewu.fusion.service.storage.StorageConfigService;
import com.stonewu.fusion.service.system.PresetArtStyleResourceResolver;
import okio.Buffer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenAiCompatibleFunAiVideoProtocolAdapterTests {

    private final OpenAiCompatibleVideoProtocolSupport support = new OpenAiCompatibleVideoProtocolSupport(
            mock(StorageConfigService.class),
            new PresetArtStyleResourceResolver()
    );
    private final OpenAiCompatibleFunAiVideoProtocolAdapter adapter =
            new OpenAiCompatibleFunAiVideoProtocolAdapter(support);

    @Test
    void buildsFunAiJsonBodyWithAllSupportedReferenceTypes() throws Exception {
        JSONObject modelConfig = JSONUtil.createObj()
                .set("negativePrompt", "flicker, watermark")
                .set("funAiExtraBody", JSONUtil.createObj()
                        .set("style_references", List.of("https://example.com/style.png")));
        VideoTask task = VideoTask.builder()
                .prompt("A cinematic product shot")
                .duration(10)
                .resolution("1920x1080")
                .firstFrameImageUrl("https://example.com/first.png")
                .referenceImageUrls(JSONUtil.toJsonStr(List.of("https://example.com/ref.png")))
                .lastFrameImageUrl("https://example.com/last.png")
                .referenceVideoUrls(JSONUtil.toJsonStr(List.of("https://example.com/motion.mp4")))
                .referenceAudioUrls(JSONUtil.toJsonStr(List.of(
                        "https://example.com/voice.mp3",
                        "https://example.com/music.wav")))
                .generateAudio(true)
                .build();
        OpenAiCompatibleVideoProtocolContext context = context(task, modelConfig, "https://api.funai.works/v1");

        Buffer buffer = new Buffer();
        adapter.buildSubmitBody(context).writeTo(buffer);
        JSONObject body = JSONUtil.parseObj(buffer.readUtf8());

        assertThat(adapter.resolveSubmitUrl(context)).isEqualTo("https://api.funai.works/v1/videos");
        assertThat(body.getStr("model")).isEqualTo("seedance-2.0");
        assertThat(body.getStr("prompt")).isEqualTo("A cinematic product shot");
        assertThat(body.getInt("seconds")).isEqualTo(10);
        assertThat(body.getStr("size")).isEqualTo("1920x1080");
        assertThat(body.getJSONArray("images").toList(String.class)).containsExactly(
                "https://example.com/first.png",
                "https://example.com/last.png");
        assertThat(body.getJSONArray("element_references").toList(String.class))
                .containsExactly("https://example.com/ref.png");
        assertThat(body.getStr("input_video")).isEqualTo("https://example.com/motion.mp4");
        assertThat(body.getJSONArray("audio_references").toList(String.class)).containsExactly(
                "https://example.com/voice.mp3",
                "https://example.com/music.wav");
        assertThat(body.getBool("generate_audio")).isTrue();
        assertThat(body.getStr("negative_prompt")).isEqualTo("flicker, watermark");
        assertThat(body.getJSONArray("style_references").toList(String.class))
                .containsExactly("https://example.com/style.png");
        assertThat(body.getInt("n")).isEqualTo(1);
    }

    @Test
    void derivesDefaultVideoSizeFromRatio() throws Exception {
        VideoTask task = VideoTask.builder()
                .prompt("Vertical city scene")
                .ratio("9:16")
                .build();

        Buffer buffer = new Buffer();
        adapter.buildSubmitBody(context(task, new JSONObject(), "https://api.funai.works"))
                .writeTo(buffer);

        assertThat(JSONUtil.parseObj(buffer.readUtf8()).getStr("size")).isEqualTo("720x1280");
    }

    @Test
    void parsesNestedCompletedTaskAndResolvesRelativeContentUrl() {
        OpenAiCompatibleVideoProtocolContext context = context(
                VideoTask.builder().prompt("test").build(),
                new JSONObject(),
                "https://api.funai.works/v1/videos");

        OpenAiCompatibleVideoTaskResult result = adapter.parseQueryResponse(context, """
                {
                  "data": {
                    "id": "video_123",
                    "status": "SUCCESS",
                    "seconds": 8,
                    "content_url": "/v1/videos/video_123/content"
                  }
                }
                """);

        assertThat(result.trackingId()).isEqualTo("video_123");
        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.durationSeconds()).isEqualTo(8);
        assertThat(adapter.resolveVideoContentUrl(context, result.trackingId(), result))
                .isEqualTo("https://api.funai.works/v1/videos/video_123/content");
    }

    @Test
    void replacesLoopbackContentHostWithConfiguredFunAiHost() {
        OpenAiCompatibleVideoProtocolContext context = context(
                VideoTask.builder().prompt("test").build(),
                new JSONObject(),
                "https://api.funai.works");
        OpenAiCompatibleVideoTaskResult result = adapter.parseQueryResponse(context, """
                {
                  "id": "video_456",
                  "status": "completed",
                  "content_url": "http://127.0.0.1:6010/v1/videos/video_456/content"
                }
                """);

        assertThat(adapter.resolveVideoContentUrl(context, result.trackingId(), result))
                .isEqualTo("https://api.funai.works/v1/videos/video_456/content");
    }

    private OpenAiCompatibleVideoProtocolContext context(VideoTask task, JSONObject modelConfig, String apiUrl) {
        return new OpenAiCompatibleVideoProtocolContext(
                AiModel.builder().code("seedance-2.0").modelType(3).build(),
                ApiConfig.builder().platform("funai").apiUrl(apiUrl).apiKey("test-key").build(),
                task,
                modelConfig,
                new AiModelMetadata("funai", "funai", "seedance", "funai")
        );
    }
}
