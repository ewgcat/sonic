/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published
 *   by the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.agent.aspect;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.cloud.sonic.agent.common.models.HandleContext;
import org.springframework.context.annotation.Configuration;

@Aspect
@Slf4j
@Configuration
public class IteratorAspect {
    @Pointcut("@annotation(org.cloud.sonic.agent.aspect.IteratorCheck)")
    public void serviceAspect() {
    }

    @Around(value = "serviceAspect()")
    public Object around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        Object[] objects = processInputArg(proceedingJoinPoint.getArgs());
        return proceedingJoinPoint.proceed(objects);
    }

    private Object[] processInputArg(Object[] args) {
        HandleContext handleContext = null;
        for (Object arg : args) {
            if (arg instanceof HandleContext) {
                handleContext = (HandleContext) arg;
                break;
            }
        }
        JSONObject paramStep = null;
        for (Object arg : args) {
            if (arg instanceof JSONObject) {
                paramStep = (JSONObject) arg;
                break;
            }
        }

        try {
            if (paramStep != null && handleContext != null && handleContext.currentIteratorElement != null) {

                String uniquelyIdentifies = handleContext.currentIteratorElement.getUniquelyIdentifies();

                JSONObject step = paramStep.getJSONObject("step");
                JSONArray eleList = step.getJSONArray("elements");

                for (int i = 0; i < eleList.size(); i++) {
                    JSONObject ele = eleList.getJSONObject(i);
                    if ("pocoIterator".equals(ele.get("eleType").toString())
                            || "androidIterator".equals(ele.get("eleType").toString())
                            || "iOSIterator".equals(ele.get("eleType").toString())) {
                        ele.put("eleValue", uniquelyIdentifies);
                    }
                    eleList.set(i, new JSONObject(ele));
                }
            }
        } catch (Throwable e) {
            log.info(e.getMessage());
        }
        return args;
    }
}
