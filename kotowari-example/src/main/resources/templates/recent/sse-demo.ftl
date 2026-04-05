<#import "/layout/defaultLayout.ftl" as layout>
<@layout.layout "SSE Demo">
  <h1>Server-Sent Events (SSE) Demo</h1>
  <p>This page demonstrates <code>SseEmitter</code> built into Enkan.
     The server pushes events over a persistent HTTP connection — no polling required.</p>

  <div class="panel panel-default">
    <div class="panel-heading"><strong>Endpoints</strong></div>
    <div class="panel-body">
      <p><strong>Countdown:</strong> <code>GET /api/sse/countdown</code> — sends 5 events (5→0) at 1 s intervals, then a <code>done</code> event.</p>
      <p><strong>Tick:</strong> <code>GET /api/sse/tick</code> — sends 10 tick events at 500 ms intervals with sequence number and timestamp.</p>
    </div>
  </div>

  <h3>Countdown</h3>
  <p>Click <strong>Start Countdown</strong> to open an SSE connection. Events appear as they arrive.</p>
  <button id="countdown-btn" class="btn btn-primary">Start Countdown</button>
  <button id="countdown-stop-btn" class="btn btn-default" style="margin-left: 8px;" disabled>Stop</button>
  <pre id="countdown-result" style="margin-top: 12px; min-height: 60px;">Press Start to begin.</pre>

  <h3>Tick Stream</h3>
  <p>Click <strong>Start Tick</strong> to stream 10 tick events (500 ms apart) with server timestamps.</p>
  <button id="tick-btn" class="btn btn-primary">Start Tick</button>
  <button id="tick-stop-btn" class="btn btn-default" style="margin-left: 8px;" disabled>Stop</button>
  <pre id="tick-result" style="margin-top: 12px; min-height: 60px;">Press Start to begin.</pre>

  <h3>Try with curl</h3>
  <pre>curl -N "http://localhost:3000/api/sse/countdown"</pre>
  <pre>curl -N "http://localhost:3000/api/sse/tick"</pre>

  <h3>Expected</h3>
  <ul>
    <li>Countdown: events arrive one per second: <code>5 4 3 2 1 0</code>, then <code>done</code>.</li>
    <li>Tick: 10 events arrive every 500 ms with <code>seq</code> and <code>ts</code> fields.</li>
    <li>Closing the stream (Stop button) sends no further events.</li>
  </ul>

  <script src="/assets/js/recent-sse-demo.js"></script>
</@layout.layout>
