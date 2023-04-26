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

package org.apache.flink.kubernetes.operator.autoscaler.utils;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.kubernetes.operator.autoscaler.config.AutoScalerOptions;
import org.apache.flink.kubernetes.operator.autoscaler.metrics.EvaluatedScalingMetric;
import org.apache.flink.kubernetes.operator.autoscaler.metrics.ScalingMetric;

import java.util.Map;

import static org.apache.flink.kubernetes.operator.autoscaler.metrics.ScalingMetric.CATCH_UP_DATA_RATE;
import static org.apache.flink.kubernetes.operator.autoscaler.metrics.ScalingMetric.TARGET_DATA_RATE;

/**
 * AutoScaler utilities.
 */
public class AutoScalerUtils {

    /**
     * @param evaluatedMetrics  job 采集到的所有指标
     * @param conf              配置
     * @param targetUtilization 目标利用率
     * @param withRestart       是否重启
     * @return
     */
    public static double getTargetProcessingCapacity(
            Map<ScalingMetric, EvaluatedScalingMetric> evaluatedMetrics,
            Configuration conf,
            double targetUtilization,
            boolean withRestart) {

        // Target = Lag Catchup Rate + Restart Catchup Rate + Processing at utilization
        // Target = LAG/CATCH_UP + INPUT_RATE*RESTART/CATCH_UP + INPUT_RATE/TARGET_UTIL

        double lagCatchupTargetRate = evaluatedMetrics.get(CATCH_UP_DATA_RATE).getCurrent();
        if (Double.isNaN(lagCatchupTargetRate)) {
            return Double.NaN;
        }

        double catchUpTargetSec = conf.get(AutoScalerOptions.CATCH_UP_DURATION).toSeconds();
        double restartTimeSec = conf.get(AutoScalerOptions.RESTART_TIME).toSeconds();

        targetUtilization = Math.max(0., targetUtilization);
        targetUtilization = Math.min(1, targetUtilization);

        double avgInputTargetRate = evaluatedMetrics.get(TARGET_DATA_RATE).getAverage();
        if (Double.isNaN(avgInputTargetRate)) {
            return Double.NaN;
        }

        if (targetUtilization == 0) {
            return Double.POSITIVE_INFINITY;
        }

        double restartCatchupRate =
                !withRestart || catchUpTargetSec == 0
                        ? 0
                        : (avgInputTargetRate * restartTimeSec) / catchUpTargetSec;
        double inputTargetAtUtilization = avgInputTargetRate / targetUtilization;

        return Math.round(lagCatchupTargetRate + restartCatchupRate + inputTargetAtUtilization);
    }
}
