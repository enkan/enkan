(function() {
    var SCRIPT = document.currentScript;
    var BASE_PATH = SCRIPT.src.substring(0, SCRIPT.src.lastIndexOf('/') + 1);
    var PROMPT = 'enkan> ';
    var MAX_OUTPUT_LOG = 500;

    var ws = null;
    var term = null;
    var fitAddon = null;
    var inputBuffer = '';
    var history = [];
    var historyIndex = -1;
    var savedInput = '';
    var waitingForResponse = false;
    var outputLog = [];

    // --- Session Storage ---

    function saveState() {
        try {
            sessionStorage.setItem('enkan-repl-history', JSON.stringify(history));
            sessionStorage.setItem('enkan-repl-output', JSON.stringify(outputLog));
        } catch (e) { /* storage full or unavailable */ }
    }

    function saveOpenState(isOpen) {
        try {
            if (isOpen) {
                sessionStorage.setItem('enkan-repl-open', 'true');
            } else {
                sessionStorage.removeItem('enkan-repl-open');
            }
        } catch (e) { /* ignore */ }
    }

    function loadState() {
        try {
            var h = sessionStorage.getItem('enkan-repl-history');
            if (h) {
                history = JSON.parse(h);
                historyIndex = history.length;
            }
            var o = sessionStorage.getItem('enkan-repl-output');
            if (o) {
                outputLog = JSON.parse(o);
            }
        } catch (e) { /* ignore */ }
    }

    function wasOpen() {
        try {
            return sessionStorage.getItem('enkan-repl-open') === 'true';
        } catch (e) { return false; }
    }

    function logOutput(type, text) {
        outputLog.push({type: type, text: text});
        if (outputLog.length > MAX_OUTPUT_LOG) {
            outputLog = outputLog.slice(outputLog.length - MAX_OUTPUT_LOG);
        }
        saveState();
    }

    function replayOutput() {
        for (var i = 0; i < outputLog.length; i++) {
            var entry = outputLog[i];
            switch (entry.type) {
                case 'cmd':
                    term.writeln(PROMPT + entry.text);
                    break;
                case 'err':
                    term.writeln('\x1b[31m' + entry.text + '\x1b[0m');
                    break;
                case 'system':
                    term.writeln('\x1b[90m' + entry.text + '\x1b[0m');
                    break;
                default: // 'out'
                    term.writeln(entry.text);
                    break;
            }
        }
    }

    // --- Dynamic Resource Loading ---

    function loadCSS(href) {
        var link = document.createElement('link');
        link.rel = 'stylesheet';
        link.href = href;
        document.head.appendChild(link);
    }

    function loadScript(src, callback) {
        var script = document.createElement('script');
        script.src = src;
        script.onload = callback;
        document.head.appendChild(script);
    }

    function loadDependencies(callback) {
        loadCSS(BASE_PATH + 'xterm.css');
        loadScript(BASE_PATH + 'xterm.js', function() {
            loadScript(BASE_PATH + 'addon-fit.js', callback);
        });
    }

    // --- Init CSS immediately (needed for toggle button styling) ---
    loadCSS(BASE_PATH + 'enkan-repl.css');

    // --- UI Construction ---

    function createUI() {
        var btn = document.createElement('button');
        btn.id = 'enkan-repl-toggle';
        btn.textContent = '>';
        btn.title = 'Toggle REPL';
        btn.addEventListener('click', toggle);
        document.body.appendChild(btn);

        var panel = document.createElement('div');
        panel.id = 'enkan-repl-panel';
        panel.innerHTML =
            '<div id="enkan-repl-header">' +
            '  <span class="title">enkan REPL</span>' +
            '  <button class="close-btn" title="Close">\u00d7</button>' +
            '</div>' +
            '<div id="enkan-repl-terminal"></div>';
        document.body.appendChild(panel);

        panel.querySelector('.close-btn').addEventListener('click', toggle);
        initDrag(panel);

        // Auto-open if was open on previous page
        if (wasOpen()) {
            openPanel();
        }
    }

    function openPanel() {
        var panel = document.getElementById('enkan-repl-panel');
        panel.classList.add('open');
        saveOpenState(true);
        if (!term) {
            loadDependencies(function() {
                initTerminal();
                replayOutput();
                connect();
                if (fitAddon) fitAddon.fit();
                term.focus();
            });
        } else {
            if (!ws) connect();
            if (fitAddon) fitAddon.fit();
            term.focus();
        }
    }

    // --- Drag ---

    function initDrag(panel) {
        var header = panel.querySelector('#enkan-repl-header');
        var dragging = false;
        var offsetX = 0, offsetY = 0;

        header.addEventListener('mousedown', function(e) {
            if (e.target.classList.contains('close-btn')) return;
            dragging = true;
            var rect = panel.getBoundingClientRect();
            offsetX = e.clientX - rect.left;
            offsetY = e.clientY - rect.top;
            e.preventDefault();
        });

        document.addEventListener('mousemove', function(e) {
            if (!dragging) return;
            var x = e.clientX - offsetX;
            var y = e.clientY - offsetY;
            // Clamp to viewport
            x = Math.max(0, Math.min(x, window.innerWidth - panel.offsetWidth));
            y = Math.max(0, Math.min(y, window.innerHeight - panel.offsetHeight));
            panel.style.left = x + 'px';
            panel.style.top = y + 'px';
            panel.style.right = 'auto';
            panel.style.bottom = 'auto';
        });

        document.addEventListener('mouseup', function() {
            dragging = false;
        });
    }

    function toggle() {
        var panel = document.getElementById('enkan-repl-panel');
        var isOpen = panel.classList.contains('open');
        if (isOpen) {
            panel.classList.remove('open');
            saveOpenState(false);
        } else {
            openPanel();
        }
    }

    // --- Terminal ---

    function initTerminal() {
        var container = document.getElementById('enkan-repl-terminal');
        term = new Terminal({
            cursorBlink: true,
            fontSize: 13,
            scrollback: 1000,
            fontFamily: "'Menlo', 'Consolas', 'DejaVu Sans Mono', monospace",
            theme: {
                background: '#2d2d30',
                foreground: '#d4d4d4',
                cursor: '#aeafad',
                red: '#f48771'
            }
        });
        fitAddon = new FitAddon.FitAddon();
        term.loadAddon(fitAddon);
        term.open(container);
        fitAddon.fit();

        term.onData(onTermData);

        window.addEventListener('resize', function() {
            if (fitAddon && document.getElementById('enkan-repl-panel').classList.contains('open')) {
                fitAddon.fit();
            }
        });
    }

    function onTermData(data) {
        if (waitingForResponse) return;

        for (var i = 0; i < data.length; i++) {
            var rest = data.substring(i);

            if (rest.startsWith('\x1b[A')) {
                historyUp();
                i += 2;
                continue;
            }
            if (rest.startsWith('\x1b[B')) {
                historyDown();
                i += 2;
                continue;
            }
            if (rest.startsWith('\x1b[C') || rest.startsWith('\x1b[D')) {
                i += 2;
                continue;
            }

            var ch = data.charCodeAt(i);

            if (ch === 13) {
                term.write('\r\n');
                var cmd = inputBuffer;
                inputBuffer = '';
                if (cmd.trim()) {
                    history.push(cmd);
                    historyIndex = history.length;
                    logOutput('cmd', cmd);
                    sendCommand(cmd);
                } else {
                    showPrompt();
                }
            } else if (ch === 127 || ch === 8) {
                if (inputBuffer.length > 0) {
                    inputBuffer = inputBuffer.substring(0, inputBuffer.length - 1);
                    term.write('\b \b');
                }
            } else if (ch === 3) {
                inputBuffer = '';
                term.write('^C\r\n');
                showPrompt();
            } else if (ch >= 32) {
                inputBuffer += data.charAt(i);
                term.write(data.charAt(i));
            }
        }
    }

    function historyUp() {
        if (history.length === 0) return;
        if (historyIndex === history.length) savedInput = inputBuffer;
        if (historyIndex > 0) {
            historyIndex--;
            replaceInput(history[historyIndex]);
        }
    }

    function historyDown() {
        if (historyIndex < history.length) {
            historyIndex++;
            replaceInput(historyIndex === history.length ? savedInput : history[historyIndex]);
        }
    }

    function replaceInput(newInput) {
        for (var i = 0; i < inputBuffer.length; i++) {
            term.write('\b \b');
        }
        inputBuffer = newInput;
        term.write(inputBuffer);
    }

    function showPrompt() {
        waitingForResponse = false;
        term.write(PROMPT);
    }

    // --- WebSocket ---

    function connect() {
        fetch('/x-enkan/dev-info')
            .then(function(r) { return r.json(); })
            .then(function(info) { doConnect(info.wsPort); })
            .catch(function() { doConnect(3001); });
    }

    function doConnect(WS_PORT) {
        ws = new WebSocket('ws://localhost:' + WS_PORT + '/repl');
        ws.onopen = function() {
            var msg = 'Connected to enkan REPL on port ' + WS_PORT;
            term.writeln(msg);
            logOutput('system', msg);
            showPrompt();
        };
        ws.onclose = function() {
            ws = null;
            if (term) {
                term.writeln('\r\n\x1b[31mDisconnected\x1b[0m');
                logOutput('system', 'Disconnected');
                showPrompt();
            }
        };
        ws.onmessage = function(e) {
            var msg = JSON.parse(e.data);
            if (msg.out) {
                term.writeln(msg.out);
                logOutput('out', msg.out);
            }
            if (msg.err) {
                term.writeln('\x1b[31m' + msg.err + '\x1b[0m');
                logOutput('err', msg.err);
            }
            if (msg.status && msg.status.indexOf('DONE') !== -1) {
                showPrompt();
            }
        };
        ws.onerror = function() {
            if (term) term.writeln('\x1b[31mConnection error\x1b[0m');
        };
    }

    function sendCommand(cmd) {
        if (!ws || ws.readyState !== WebSocket.OPEN) {
            term.writeln('\x1b[31mNot connected\x1b[0m');
            showPrompt();
            return;
        }
        waitingForResponse = true;
        ws.send(cmd);
    }

    // --- Init ---

    loadState();

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', createUI);
    } else {
        createUI();
    }
})();
