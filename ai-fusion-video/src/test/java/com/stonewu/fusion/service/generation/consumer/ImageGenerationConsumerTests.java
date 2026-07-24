package com.stonewu.fusion.service.generation.consumer;

import com.stonewu.fusion.service.generation.strategy.ImageGenerationStrategy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class ImageGenerationConsumerTests {

    @Test
    void shouldResolveGoogleFlowStrategyIgnoringPlatformCase() throws Exception {
        ImageGenerationStrategy vertexStrategy = mock(ImageGenerationStrategy.class);
        ImageGenerationStrategy flowStrategy = mock(ImageGenerationStrategy.class);
        Map<String, ImageGenerationStrategy> strategyMap = new LinkedHashMap<>();
        strategyMap.put("vertex_ai", vertexStrategy);
        strategyMap.put("GoogleFlowReverseApi", flowStrategy);

        ImageGenerationConsumer consumer = new ImageGenerationConsumer(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        Method method = ImageGenerationConsumer.class.getDeclaredMethod(
                "resolveStrategyByPlatform",
                Map.class,
                String.class
        );
        method.setAccessible(true);

        ImageGenerationStrategy resolved = (ImageGenerationStrategy) method.invoke(
                consumer,
                strategyMap,
                "googleflowreverseapi"
        );

        assertSame(flowStrategy, resolved);
    }

    @Test
    void shouldResolveFunAiImagesThroughOpenAiCompatibleStrategy() throws Exception {
        ImageGenerationStrategy openAiStrategy = mock(ImageGenerationStrategy.class);
        Map<String, ImageGenerationStrategy> strategyMap = new LinkedHashMap<>();
        strategyMap.put("openai_compatible", openAiStrategy);

        ImageGenerationConsumer consumer = new ImageGenerationConsumer(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        Method method = ImageGenerationConsumer.class.getDeclaredMethod(
                "resolveStrategyByPlatform",
                Map.class,
                String.class
        );
        method.setAccessible(true);

        ImageGenerationStrategy resolved = (ImageGenerationStrategy) method.invoke(
                consumer,
                strategyMap,
                "funai"
        );

        assertSame(openAiStrategy, resolved);
    }
}
