/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.namesrv.processor;

import io.netty.channel.ChannelHandlerContext;
import org.apache.rocketmq.common.help.FAQUrl;
import org.apache.rocketmq.common.namesrv.NamesrvUtil;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.header.namesrv.GetRouteInfoRequestHeader;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.common.protocol.route.TopicRouteDatas;
import org.apache.rocketmq.namesrv.NamesrvController;
import org.apache.rocketmq.remoting.exception.RemotingCommandException;
import org.apache.rocketmq.remoting.netty.NettyRequestProcessor;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;

public class ClientRequestProcessor implements NettyRequestProcessor {
    protected NamesrvController namesrvController;

    public ClientRequestProcessor(final NamesrvController namesrvController) {
        this.namesrvController = namesrvController;
    }

    @Override
    public RemotingCommand processRequest(final ChannelHandlerContext ctx,
        final RemotingCommand request) throws Exception {
        return this.getRouteInfoByTopic(ctx, request);
    }

    public RemotingCommand getRouteInfoByTopic(ChannelHandlerContext ctx,
        RemotingCommand request) throws RemotingCommandException {
        final RemotingCommand response = RemotingCommand.createResponseCommand(null);
        final GetRouteInfoRequestHeader requestHeader =
            (GetRouteInfoRequestHeader) request.decodeCommandCustomHeader(GetRouteInfoRequestHeader.class);

        if (requestHeader.getTopic().indexOf(GetRouteInfoRequestHeader.split) < 0) {
            TopicRouteData topicRouteData = this.namesrvController.getRouteInfoManager().pickupTopicRouteData(requestHeader.getTopic());

            if (topicRouteData != null) {
                if (this.namesrvController.getNamesrvConfig().isOrderMessageEnable()) {
                    String orderTopicConf =
                        this.namesrvController.getKvConfigManager().getKVConfig(NamesrvUtil.NAMESPACE_ORDER_TOPIC_CONFIG,
                            requestHeader.getTopic());
                    topicRouteData.setOrderTopicConf(orderTopicConf);
                }

                byte[] content = topicRouteData.encode();
                response.setBody(content);
                response.setCode(ResponseCode.SUCCESS);
                response.setRemark(null);
                return response;
            }

            response.setCode(ResponseCode.TOPIC_NOT_EXIST);
            response.setRemark("No topic route info in name server for the topic: " + requestHeader.getTopic()
                + FAQUrl.suggestTodo(FAQUrl.APPLY_TOPIC_URL));
            return response;
        }

        String[] topics = requestHeader.getTopic().split(String.valueOf(GetRouteInfoRequestHeader.split));
        TopicRouteDatas topicRouteDatas = new TopicRouteDatas();

        for (String topic : topics) {
            //调用RouteInfoManager的方法,从路由表topicQueueTable、brokerAddrTable、filterServerTable中分别填充TopicRouteData的List<QueueData>、List<BrokerData>、filterServer
            TopicRouteData topicRouteData = this.namesrvController.getRouteInfoManager().pickupTopicRouteData(topic);
            if (topicRouteData == null) {
                continue;
            }
            // 如果找到主题对应你的路由信息并且该主题为顺序消息，则从NameServer KVConfig中获取关于顺序消息相关的配置填充路由信息
            if (this.namesrvController.getNamesrvConfig().isOrderMessageEnable()) {
                String orderTopicConf =
                    this.namesrvController.getKvConfigManager().getKVConfig(NamesrvUtil.NAMESPACE_ORDER_TOPIC_CONFIG, topic);
                topicRouteData.setOrderTopicConf(orderTopicConf);
            }

            topicRouteDatas.getTopics().put(topic, topicRouteData);
        }
        response.setBody(topicRouteDatas.encode());
        response.setCode(ResponseCode.SUCCESS);
        response.setRemark(null);
        return response;
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}
