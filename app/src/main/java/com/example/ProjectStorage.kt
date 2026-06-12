package com.example

import android.content.Context
import android.os.Environment
import java.io.File

object ProjectStorage {
    fun getBaseDir(context: Context): File {
        // Primary: Documents/JS-Sandbox
        val publicDoc = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dir = File(publicDoc, "JS-Sandbox")
        return try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            // Test write permission
            val testFile = File(dir, ".test")
            testFile.writeText("test")
            testFile.delete()
            dir
        } catch (e: Exception) {
            // Fallback to internal app storage
            val fallbackDir = File(context.filesDir, "JS-Sandbox")
            if (!fallbackDir.exists()) {
                fallbackDir.mkdirs()
            }
            fallbackDir
        }
    }

    fun bootstrapDemoProject(context: Context) {
        val baseDir = getBaseDir(context)
        val demoProjectDir = File(baseDir, "Demo_Getar_Alarm")
        if (!demoProjectDir.exists()) {
            demoProjectDir.mkdirs()
        }

        // 1. Create config.json
        val configFile = File(demoProjectDir, "config.json")
        if (!configFile.exists()) {
            configFile.writeText("""
                {
                  "name": "Demo Getar & Alarm",
                  "icon": "vibration",
                  "description": "Contoh aplikasi JS-Sandbox untuk menggetarkan perangkat, memasang alarm, dan menjalankan service latar belakang."
                }
            """.trimIndent())
        }

        // 2. Create main.js
        val mainFile = File(demoProjectDir, "main.js")
        if (!mainFile.exists()) {
            mainFile.writeText("""
                // Main entrypoint for DOM manipulation inside sandbox WebView container.
                import { showToast, vibrate, createAlarm, startBackgroundService } from './modules/utils.js';

                const app = document.getElementById('app');

                // Add visual header card
                const headerCard = document.createElement('div');
                headerCard.style.background = 'linear-gradient(135deg, #6200ee, #03dac6)';
                headerCard.style.padding = '24px 16px';
                headerCard.style.borderRadius = '16px';
                headerCard.style.color = '#ffffff';
                headerCard.style.marginBottom = '20px';
                headerCard.style.boxShadow = '0 4px 12px rgba(0,0,0,0.3)';
                headerCard.style.textAlign = 'center';

                const title = document.createElement('h2');
                title.innerText = '🤖 JS Sandbox Builder';
                title.style.margin = '0 0 8px 0';
                title.style.fontSize = '24px';
                title.style.fontWeight = 'bold';
                headerCard.appendChild(title);

                const subtitle = document.createElement('p');
                subtitle.innerText = 'Platform JavaScript DOM native Android Bridge';
                subtitle.style.margin = '0';
                subtitle.style.fontSize = '14px';
                subtitle.style.opacity = '0.9';
                headerCard.appendChild(subtitle);

                app.appendChild(headerCard);

                // Add Action Buttons Container
                const actionGroup = document.createElement('div');
                actionGroup.style.display = 'flex';
                actionGroup.style.flexDirection = 'column';
                actionGroup.style.gap = '14px';
                actionGroup.style.padding = '8px';
                app.appendChild(actionGroup);

                // 1. Vibrate Button
                const btnVibrate = document.createElement('button');
                btnVibrate.innerText = '📳 Getarkan HP (500ms)';
                btnVibrate.style.padding = '14px';
                btnVibrate.style.backgroundColor = '#1e1e2e';
                btnVibrate.style.color = '#03dac6';
                btnVibrate.style.border = '2px solid #03dac6';
                btnVibrate.style.borderRadius = '12px';
                btnVibrate.style.fontSize = '16px';
                btnVibrate.style.fontWeight = 'bold';
                btnVibrate.style.cursor = 'pointer';
                btnVibrate.onclick = () => {
                    vibrate(500);
                    showToast('HP Menggetar 500 Milidetik!');
                };
                actionGroup.appendChild(btnVibrate);

                // 2. Alarm Section
                const alarmCard = document.createElement('div');
                alarmCard.style.backgroundColor = '#1e1e2e';
                alarmCard.style.border = '1px solid #333344';
                alarmCard.style.borderRadius = '12px';
                alarmCard.style.padding = '16px';
                alarmCard.style.display = 'flex';
                alarmCard.style.flexDirection = 'column';
                alarmCard.style.gap = '12px';

                const sectionTitle = document.createElement('div');
                sectionTitle.innerText = '⏰ Jadwalkan Alarm Baru';
                sectionTitle.style.color = '#bb86fc';
                sectionTitle.style.fontWeight = 'bold';
                sectionTitle.style.fontSize = '16px';
                alarmCard.appendChild(sectionTitle);

                const inputRow = document.createElement('div');
                inputRow.style.display = 'flex';
                inputRow.style.gap = '8px';

                const inputLabel = document.createElement('input');
                inputLabel.type = 'text';
                inputLabel.value = 'Makan Siang';
                inputLabel.placeholder = 'Label Alarm';
                inputLabel.style.flex = '1';
                inputLabel.style.padding = '10px';
                inputLabel.style.backgroundColor = '#121214';
                inputLabel.style.color = 'white';
                inputLabel.style.border = '1px solid #444';
                inputLabel.style.borderRadius = '8px';

                const inputTime = document.createElement('input');
                inputTime.type = 'time';
                inputTime.value = '12:00';
                inputTime.style.padding = '10px';
                inputTime.style.backgroundColor = '#121214';
                inputTime.style.color = 'white';
                inputTime.style.border = '1px solid #444';
                inputTime.style.borderRadius = '8px';

                inputRow.appendChild(inputLabel);
                inputRow.appendChild(inputTime);
                alarmCard.appendChild(inputRow);

                const btnAlarm = document.createElement('button');
                btnAlarm.innerText = 'Pasang Sistem Alarm';
                btnAlarm.style.padding = '12px';
                btnAlarm.style.backgroundColor = '#bb86fc';
                btnAlarm.style.color = '#121212';
                btnAlarm.style.border = 'none';
                btnAlarm.style.borderRadius = '8px';
                btnAlarm.style.fontWeight = 'bold';
                btnAlarm.style.cursor = 'pointer';
                btnAlarm.onclick = () => {
                    const timeValue = inputTime.value;
                    const parts = timeValue.split(':');
                    if (parts.length === 2) {
                        const hour = parseInt(parts[0], 10);
                        const minute = parseInt(parts[1], 10);
                        createAlarm(inputLabel.value, hour, minute);
                        showToast('Alarm ' + inputLabel.value + ' berhasil diset jam ' + timeValue);
                    }
                };
                alarmCard.appendChild(btnAlarm);
                actionGroup.appendChild(alarmCard);

                // 3. Foreground Service Card
                const serviceCard = document.createElement('div');
                serviceCard.style.backgroundColor = '#1e1e2e';
                serviceCard.style.border = '1px solid #333344';
                serviceCard.style.borderRadius = '12px';
                serviceCard.style.padding = '16px';
                serviceCard.style.display = 'flex';
                serviceCard.style.flexDirection = 'column';
                serviceCard.style.gap = '10px';

                const servTitle = document.createElement('div');
                servTitle.innerText = '⚙️ Headless Background Service';
                servTitle.style.color = '#cf6679';
                servTitle.style.fontWeight = 'bold';
                servTitle.style.fontSize = '16px';
                serviceCard.appendChild(servTitle);

                const btnService = document.createElement('button');
                btnService.innerText = '🚀 Jalankan Layanan Headless';
                btnService.style.padding = '12px';
                btnService.style.backgroundColor = '#cf6679';
                btnService.style.color = 'white';
                btnService.style.border = 'none';
                btnService.style.borderRadius = '8px';
                btnService.style.fontWeight = 'bold';
                btnService.style.cursor = 'pointer';
                btnService.onclick = () => {
                    startBackgroundService('service.js');
                    showToast('Background Service Dijalankan!');
                };
                serviceCard.appendChild(btnService);

                const msgArea = document.createElement('div');
                msgArea.innerText = 'Menunggu pesan dari service.js...';
                msgArea.style.padding = '10px';
                msgArea.style.backgroundColor = '#121214';
                msgArea.style.border = '1px dashed #cf6679';
                msgArea.style.borderRadius = '8px';
                msgArea.style.fontSize = '13px';
                msgArea.style.color = '#cccccc';
                msgArea.id = 'msg-log';
                serviceCard.appendChild(msgArea);

                actionGroup.appendChild(serviceCard);

                // Service Message Event Handler
                window.onServiceMessage = (data) => {
                    const el = document.getElementById('msg-log');
                    if (el) {
                        el.innerText = '📬 Masuk service: ' + data;
                    }
                };
            """.trimIndent())
        }

        // 3. Create modules/utils.js
        val modulesDir = File(demoProjectDir, "modules")
        if (!modulesDir.exists()) {
            modulesDir.mkdirs()
        }
        val utilsFile = File(modulesDir, "utils.js")
        if (!utilsFile.exists()) {
            utilsFile.writeText("""
                // JS-Sandbox bridge utilities wrapper.
                // Call interfaces bound to JavaScript context named 'android'.
                
                export function showToast(message) {
                    if (window.android && window.android.showToast) {
                        window.android.showToast(message);
                    } else {
                        console.log('Toast:', message);
                    }
                }

                export function vibrate(ms) {
                    if (window.android && window.android.vibrate) {
                        window.android.vibrate(ms);
                    } else {
                        console.log('Vibrate:', ms);
                    }
                }

                export function createAlarm(label, hour, minute) {
                    if (window.android && window.android.createAlarm) {
                        window.android.createAlarm(label, hour, minute);
                    } else {
                        console.log('Alarm:', label, hour, minute);
                    }
                }

                export function startBackgroundService(filename) {
                    if (window.android && window.android.startBackgroundService) {
                        window.android.startBackgroundService(filename);
                    } else {
                        console.log('Start Background Service:', filename);
                    }
                }
            """.trimIndent())
        }

        // 4. Create service.js
        val serviceFile = File(demoProjectDir, "service.js")
        if (!serviceFile.exists()) {
            serviceFile.writeText("""
                // service.js running as headless script inside Android Foreground Service
                // Sends messages back to UI thread periodically
                let sec = 0;
                setInterval(() => {
                    sec += 1;
                    sendMessageToUI("Sudah " + sec + " detik di latar belakang!");
                }, 1000);
            """.trimIndent())
        }

        // 5. Create el.js library inside the project directory so it shows up in the editor
        val libraryFile = File(demoProjectDir, "el.js")
        if (!libraryFile.exists()) {
            libraryFile.writeText(JsLibrary.EL_JS_CONTENT)
        }
    }
}
