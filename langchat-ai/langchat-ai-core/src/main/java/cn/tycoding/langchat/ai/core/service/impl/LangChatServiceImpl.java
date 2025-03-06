/*
 * Copyright (c) 2024 LangChat. TyCoding All Rights Reserved.
 *
 * Licensed under the GNU Affero General Public License, Version 3 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gnu.org/licenses/agpl-3.0.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.tycoding.langchat.ai.core.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.tycoding.langchat.ai.core.provider.EmbeddingProvider;
import cn.tycoding.langchat.ai.core.provider.ModelProvider;
import cn.tycoding.langchat.ai.core.service.Agent;
import cn.tycoding.langchat.ai.core.service.LangChatService;
import cn.tycoding.langchat.common.ai.dto.ChatReq;
import cn.tycoding.langchat.common.ai.dto.ImageR;
import cn.tycoding.langchat.common.ai.properties.ChatProps;
import cn.tycoding.langchat.common.ai.utils.PromptUtil;
import cn.tycoding.langchat.common.core.exception.ServiceException;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Function;

import static cn.tycoding.langchat.ai.core.consts.EmbedConst.KNOWLEDGE;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * @author tycoding
 * @since 2024/3/8
 */
@Slf4j
@Service
@AllArgsConstructor
public class LangChatServiceImpl implements LangChatService {

    private final ModelProvider provider;
    private final EmbeddingProvider embeddingProvider;
    private final ChatProps chatProps;

    private AiServices<Agent> build(StreamingChatLanguageModel streamModel, ChatLanguageModel model, ChatReq req) {
        /**
         * 构建并配置AiServices实例，用于与AI代理进行交互
         *
         * 函数逻辑说明：
         * 1. 创建基础AiServices构建器，绑定Agent类型
         * 2. 配置对话记忆系统：使用持久化存储的消息窗口，支持最大历史消息数量限制
         * 3. 可选配置系统提示语（当请求中包含提示文本时）
         * 4. 根据运行时条件选择流式模型或普通聊天模型
         *
         * @param req 请求对象，需包含对话ID和可选提示文本（conversationId, promptText）
         * @param streamModel 流式响应模型（可选，与普通模型互斥）
         * @param model 普通聊天模型（可选，与流式模型互斥）
         * @param chatProps 聊天配置属性，包含记忆消息数量限制（memoryMaxMessage）
         * @return 完全配置的AiServices实例，可用于与AI代理的交互
         */
        // 初始化AI服务构建器，绑定代理类并配置记忆系统
        AiServices<Agent> aiServices = AiServices.builder(Agent.class)
                // 创建基于消息窗口的对话记忆系统
                // 使用持久化存储保持对话历史，配置最大保留消息数
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(req.getConversationId())
                        .chatMemoryStore(new PersistentChatMemoryStore())
                        .maxMessages(chatProps.getMemoryMaxMessage())
                        .build());

        // 当请求包含自定义系统提示语时，配置动态提示语提供器
        if (StrUtil.isNotBlank(req.getPromptText())) {
            aiServices.systemMessageProvider(memoryId -> req.getPromptText());
        }

        // 优先使用流式模型（当可用时），否则使用普通聊天模型
        if (streamModel != null) {
            aiServices.streamingChatLanguageModel(streamModel);
        }
        if (model != null) {
            aiServices.chatLanguageModel(model);
        }
        return aiServices;
    }

    /**
     * 构建并执行流式聊天对话处理流程
     *
     * @param req 请求参数对象，包含模型ID、会话ID、消息内容、知识库ID等配置参数
     * @return 返回流式聊天响应结果，以异步流的形式逐步返回AI生成内容
     */
    @Override
    public TokenStream chat(ChatReq req) {
        // 初始化流式聊天语言模型，根据请求中的模型ID获取对应模型实例
        StreamingChatLanguageModel model = provider.stream(req.getModelId());

        // 会话ID生成逻辑：如果请求中没有会话ID，则生成新的UUID作为会话标识
        if (StrUtil.isBlank(req.getConversationId())) {
            req.setConversationId(IdUtil.simpleUUID());
        }
        // 构建AI服务实例，使用模型和请求参数进行初始化配置
        AiServices<Agent> aiServices = build(model, null, req);
        // 知识库ID处理：将单个知识库ID合并到知识库ID集合中
        if (StrUtil.isNotBlank(req.getKnowledgeId())) {
            req.getKnowledgeIds().add(req.getKnowledgeId());
        }
        // 知识增强检索配置：当存在知识库ID时，构建基于知识库的检索增强器
        if (req.getKnowledgeIds() != null && !req.getKnowledgeIds().isEmpty()) {
            // 创建动态元数据过滤器，根据请求中的知识库ID进行内容过滤
            Function<Query, Filter> filter = (query) -> metadataKey(KNOWLEDGE).isIn(req.getKnowledgeIds());

            // 构建内容检索器，配置向量存储库和嵌入模型，应用动态过滤器
            ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingProvider.getEmbeddingStore(req.getKnowledgeIds()))
                    .embeddingModel(embeddingProvider.getEmbeddingModel(req.getKnowledgeIds()))
                    .dynamicFilter(filter)
                    .build();

            // 为AI服务配置检索增强功能，将内容检索器集成到增强器中
            aiServices.retrievalAugmentor(DefaultRetrievalAugmentor
                    .builder()
                    .contentRetriever(contentRetriever)
                    .build());
        }

        // 创建最终代理实例，执行流式对话处理并返回结果
        Agent agent = aiServices.build();
        return agent.stream(req.getConversationId(), req.getMessage());
    }

    @Override
    public TokenStream singleChat(ChatReq req) {
        StreamingChatLanguageModel model = provider.stream(req.getModelId());
        if (StrUtil.isBlank(req.getConversationId())) {
            req.setConversationId(IdUtil.simpleUUID());
        }

        Agent agent = build(model, null, req).build();
        if (req.getPrompt() == null) {
            req.setPrompt(PromptUtil.build(req.getMessage(), req.getPromptText()));
        }
        return agent.stream(req.getConversationId(), req.getPrompt().text());
    }

    @Override
    public String text(ChatReq req) {
        if (StrUtil.isBlank(req.getConversationId())) {
            req.setConversationId(IdUtil.simpleUUID());
        }

        try {
            ChatLanguageModel model = provider.text(req.getModelId());
            Agent agent = build(null, model, req).build();
            String text = agent.text(req.getConversationId(), req.getMessage());
            return text;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Response<Image> image(ImageR req) {
        try {
            ImageModel model = provider.image(req.getModelId());
            return model.generate(req.getPrompt().text());
        } catch (Exception e) {
            e.printStackTrace();
            throw new ServiceException("图片生成失败");
        }
    }
}
