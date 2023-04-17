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
package org.cloud.sonic.agent.bridge.android;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.common.maps.GlobalProcessMap;
import org.cloud.sonic.agent.tests.LogUtil;
import org.cloud.sonic.agent.tools.AgentManagerTool;
import org.cloud.sonic.agent.tools.BytesTool;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.agent.tools.ProcessCommandTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import static org.cloud.sonic.agent.tools.BytesTool.sendText;

@Slf4j
@ConditionalOnProperty(value = "modules.android.use-sas", havingValue = "true")
@Component
public class AndroidSupplyTool implements ApplicationListener<ContextRefreshedEvent> {
    private static File sasBinary = new File("plugins" + File.separator + "sonic-android-supply");
    private static String sas = sasBinary.getAbsolutePath();

    @Value("${sonic.sas}")
    private String sasVersion;

    @Value("${modules.android.use-sas}")
    private boolean isSasEnable;
    private static boolean isEnable;

    @Bean
    public void setSasEnv() {
        isEnable = isSasEnable;
    }

    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        init();
        log.info("Enable sonic-android-supply Module");
    }

    private void init() {
        sasBinary.setExecutable(true);
        sasBinary.setWritable(true);
        sasBinary.setReadable(true);
        List<String> ver = ProcessCommandTool.getProcessLocalCommand(String.format("%s version", sas));
        if (ver.size() == 0 || !BytesTool.versionCheck(sasVersion, ver.get(0))) {
            log.info(String.format("Start sonic-android-supply failed! Please check sonic-android-supply version or use [chmod -R 777 %s], if still failed, you can try with [sudo]", new File("plugins").getAbsolutePath()));
            AgentManagerTool.stop();
        }
    }

    public static void startShare(String udId, Session session) {
        JSONObject sasJSON = new JSONObject();
        sasJSON.put("msg", "sas");
        if (isEnable) {
            sasJSON.put("isEnable", true);
            stopShare(udId);
            String processName = String.format("process-%s-sas", udId);
            String commandLine = "%s share -s %s --translate-port %d";
            try {
                String system = System.getProperty("os.name").toLowerCase();
                Process ps = null;
                int port = PortTool.getPort();
                if (system.contains("win")) {
                    ps = Runtime.getRuntime().exec(new String[]{"cmd", "/c", String.format(commandLine, sas, udId, port)});
                } else if (system.contains("linux") || system.contains("mac")) {
                    ps = Runtime.getRuntime().exec(new String[]{"sh", "-c", String.format(commandLine, sas, udId, port)});
                }
                GlobalProcessMap.getMap().put(processName, ps);
                sasJSON.put("port", port);
            } catch (Exception e) {
                sasJSON.put("port", 0);
                e.printStackTrace();
            } finally {
                BytesTool.sendText(session, sasJSON.toJSONString());
            }
        } else {
            sasJSON.put("isEnable", false);
            BytesTool.sendText(session, sasJSON.toJSONString());
        }
    }

    public static void startShare(String udId, int port) {
        stopShare(udId);
        String processName = String.format("process-%s-sas", udId);
        String commandLine = "%s share -s %s --translate-port %d";
        try {
            String system = System.getProperty("os.name").toLowerCase();
            Process ps = null;
            if (system.contains("win")) {
                ps = Runtime.getRuntime().exec(new String[]{"cmd", "/c", String.format(commandLine, sas, udId, port)});
            } else if (system.contains("linux") || system.contains("mac")) {
                ps = Runtime.getRuntime().exec(new String[]{"sh", "-c", String.format(commandLine, sas, udId, port)});
            }
            GlobalProcessMap.getMap().put(processName, ps);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void stopShare(String udId) {
        String processName = String.format("process-%s-sas", udId);
        if (GlobalProcessMap.getMap().get(processName) != null) {
            Process ps = GlobalProcessMap.getMap().get(processName);
            ps.children().forEach(ProcessHandle::destroy);
            ps.destroy();
        }
    }

    public static void stopPerfmon(String udId) {
        String processName = String.format("process-%s-perfmon", udId);
        if (GlobalProcessMap.getMap().get(processName) != null) {
            Process ps = GlobalProcessMap.getMap().get(processName);
            ps.children().forEach(ProcessHandle::destroy);
            ps.destroy();
        }
    }

    public static void startPerfmon(String udId, String pkg, Session session, LogUtil logUtil, int interval) {
        stopPerfmon(udId);
        if (isEnable) {
            Process ps = null;
            String commandLine = "%s perfmon -s %s -r %d %s -j --sys-cpu --sys-mem --sys-network";
            String system = System.getProperty("os.name").toLowerCase();
            String tail = pkg.length() == 0 ? "" : (" --proc-cpu --proc-fps --proc-mem --proc-threads -p " + pkg);
            try {
                if (system.contains("win")) {
                    ps = Runtime.getRuntime().exec(new String[]{"cmd", "/c", String.format(commandLine, sas, udId, interval, tail)});
                } else if (system.contains("linux") || system.contains("mac")) {
                    ps = Runtime.getRuntime().exec(new String[]{"sh", "-c", String.format(commandLine, sas, udId, interval, tail)});
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            InputStreamReader inputStreamReader = new InputStreamReader(ps.getInputStream());
            BufferedReader stdInput = new BufferedReader(inputStreamReader);
            InputStreamReader err = new InputStreamReader(ps.getErrorStream());
            BufferedReader stdInputErr = new BufferedReader(err);
            Thread psErr = new Thread(() -> {
                String s;
                while (true) {
                    try {
                        if ((s = stdInputErr.readLine()) == null) break;
                    } catch (IOException e) {
                        log.info(e.getMessage());
                        break;
                    }
                    log.info(s);
                }
                try {
                    stdInputErr.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    err.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                log.info("perfmon print thread shutdown.");
            });
            psErr.start();
            Thread pro = new Thread(() -> {
                String s;
                while (true) {
                    try {
                        if ((s = stdInput.readLine()) == null) break;
                    } catch (IOException e) {
                        log.info(e.getMessage());
                        break;
                    }
                    try {
                        JSONObject perf = JSON.parseObject(s);
                        if (session != null) {
                            JSONObject perfDetail = new JSONObject();
                            perfDetail.put("msg", "perfDetail");
                            perfDetail.put("detail", perf);
                            sendText(session, perfDetail.toJSONString());
                        }
                        if (logUtil != null) {
                            logUtil.sendPerLog(perf.toJSONString());
                        }
                    } catch (Exception ignored) {
                    }
                }
                try {
                    stdInput.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    inputStreamReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                log.info("perfmon print thread shutdown.");
            });
            pro.start();
            String processName = String.format("process-%s-perfmon", udId);
            GlobalProcessMap.getMap().put(processName, ps);
        } else {
            log.info("sonic-android-supply is not enable, please open it in config file.");
        }
    }
}
