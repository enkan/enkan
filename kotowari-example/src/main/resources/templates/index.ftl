<#import "layout/defaultLayout.ftl" as layout>
<@layout.layout "Example">
  <h1>Kotowari examples</h1>
  <ul>
    <li><a href="${urlFor("kotowari.example.controller.MiscController", "counter")}">Counter (Using session)</a></li>
    <li><a href="${urlFor("kotowari.example.controller.MiscController", "uploadForm")}">File upload</a></li>
    <li><a href="${urlFor("kotowari.example.controller.CustomerController", "index")}">CRUD</a></li>
    <li><a href="${urlFor("kotowari.example.controller.guestbook.GuestbookController", "list")}">Guestbook (with login)</a></li>
    <li><a href="${urlFor("kotowari.example.controller.ConversationStateController", "page1")}">Conversation</a></li>
  </ul>
  <hr/>
  <h3>Recent Security/Runtime Demos</h3>
  <ul>
    <li><a href="/recent/http-integrity/demo">HTTP Integrity demo (RFC 9530 + RFC 9421)</a></li>
    <li><a href="/recent/idempotency/demo">Idempotency-Key demo</a></li>
    <li><a href="/recent/jwt/demo">JWT demo (HS256)</a></li>
    <li><a href="/recent/security/csp-nonce">CSP Nonce demo</a></li>
    <li><a href="/recent/security/request-timeout">Request Timeout demo</a></li>
    <li><a href="/recent/security/fetch-metadata">Fetch Metadata demo</a></li>
    <li><a href="${urlFor("kotowari.example.controller.HospitalityDemoController", "misconfiguration")}">Misconfiguration demo</a></li>
    <li><a href="${urlFor("kotowari.example.controller.HospitalityDemoController", "unreachable")}">Unreachable Exception demo</a></li>
  </ul>
</@layout.layout>
