/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agent.grpc.handler;

import io.grpc.stub.StreamObserver;
import java.util.List;
import org.skywalking.apm.collector.agent.stream.AgentStreamModule;
import org.skywalking.apm.collector.agent.stream.service.jvm.ICpuMetricService;
import org.skywalking.apm.collector.agent.stream.service.jvm.IGCMetricService;
import org.skywalking.apm.collector.agent.stream.service.jvm.IInstanceHeartBeatService;
import org.skywalking.apm.collector.agent.stream.service.jvm.IMemoryMetricService;
import org.skywalking.apm.collector.agent.stream.service.jvm.IMemoryPoolMetricService;
import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.server.grpc.GRPCHandler;
import org.skywalking.apm.network.proto.CPU;
import org.skywalking.apm.network.proto.Downstream;
import org.skywalking.apm.network.proto.GC;
import org.skywalking.apm.network.proto.JVMMetrics;
import org.skywalking.apm.network.proto.JVMMetricsServiceGrpc;
import org.skywalking.apm.network.proto.Memory;
import org.skywalking.apm.network.proto.MemoryPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class JVMMetricsServiceHandler extends JVMMetricsServiceGrpc.JVMMetricsServiceImplBase implements GRPCHandler {

    private final Logger logger = LoggerFactory.getLogger(JVMMetricsServiceHandler.class);

    private final ICpuMetricService cpuMetricService;
    private final IGCMetricService gcMetricService;
    private final IMemoryMetricService memoryMetricService;
    private final IMemoryPoolMetricService memoryPoolMetricService;
    private final IInstanceHeartBeatService instanceHeartBeatService;

    public JVMMetricsServiceHandler(ModuleManager moduleManager) {
        this.cpuMetricService = moduleManager.find(AgentStreamModule.NAME).getService(ICpuMetricService.class);
        this.gcMetricService = moduleManager.find(AgentStreamModule.NAME).getService(IGCMetricService.class);
        this.memoryMetricService = moduleManager.find(AgentStreamModule.NAME).getService(IMemoryMetricService.class);
        this.memoryPoolMetricService = moduleManager.find(AgentStreamModule.NAME).getService(IMemoryPoolMetricService.class);
        this.instanceHeartBeatService = moduleManager.find(AgentStreamModule.NAME).getService(IInstanceHeartBeatService.class);
    }

    @Override public void collect(JVMMetrics request, StreamObserver<Downstream> responseObserver) {
        int instanceId = request.getApplicationInstanceId();
        logger.debug("receive the jvm metric from application instance, id: {}", instanceId);

        request.getMetricsList().forEach(metric -> {
            long time = TimeBucketUtils.INSTANCE.getSecondTimeBucket(metric.getTime());
            sendToInstanceHeartBeatService(instanceId, metric.getTime());
            sendToCpuMetricService(instanceId, time, metric.getCpu());
            sendToMemoryMetricService(instanceId, time, metric.getMemoryList());
            sendToMemoryPoolMetricService(instanceId, time, metric.getMemoryPoolList());
            sendToGCMetricService(instanceId, time, metric.getGcList());
        });

        responseObserver.onNext(Downstream.newBuilder().build());
        responseObserver.onCompleted();
    }

    private void sendToInstanceHeartBeatService(int instanceId, long heartBeatTime) {
        instanceHeartBeatService.send(instanceId, heartBeatTime);
    }

    private void sendToMemoryMetricService(int instanceId, long timeBucket, List<Memory> memories) {
        memories.forEach(memory -> memoryMetricService.send(instanceId, timeBucket, memory.getIsHeap(), memory.getInit(), memory.getMax(), memory.getUsed(), memory.getCommitted()));
    }

    private void sendToMemoryPoolMetricService(int instanceId, long timeBucket,
        List<MemoryPool> memoryPools) {

        memoryPools.forEach(memoryPool -> memoryPoolMetricService.send(instanceId, timeBucket, memoryPool.getType().getNumber(), memoryPool.getInit(), memoryPool.getMax(), memoryPool.getUsed(), memoryPool.getCommited()));
    }

    private void sendToCpuMetricService(int instanceId, long timeBucket, CPU cpu) {
        cpuMetricService.send(instanceId, timeBucket, cpu.getUsagePercent());
    }

    private void sendToGCMetricService(int instanceId, long timeBucket, List<GC> gcs) {
        gcs.forEach(gc -> gcMetricService.send(instanceId, timeBucket, gc.getPhraseValue(), gc.getCount(), gc.getTime()));
    }
}
