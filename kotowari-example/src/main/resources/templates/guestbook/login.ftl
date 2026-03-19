<#import "../layout/defaultLayout.ftl" as layout>
<@layout.layout "Login">
<form method="post" action="${urlFor("login")}">
    <input type="hidden" name="__conversation-token" value="${conversationToken!''}"/>
    <input type="hidden" name="url" value="${url!''}"/>
    <div class="form-group">
      <label for="email">Email Address</label>
      <input id="email" class="form-control" type="text" name="email"/>
    </div>
    <div class="form-group">
      <label for="name">Password</label>
      <input id="password" class="form-control" type="password" name="password"/>
    </div>
    <button type="submit" class="btn btn-primary">Login</button>
</form>
</@layout.layout>
