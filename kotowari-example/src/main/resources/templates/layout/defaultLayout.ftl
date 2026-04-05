<#macro layout title="Layout example">
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta http-equiv="x-ua-compatible" content="ie=edge">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title}</title>
  <link href="https://cdn.jsdelivr.net/bootstrap/3.3.6/css/bootstrap.min.css" rel="stylesheet"/>
<body>
  <div class="container">
    <#nested/>
  </div>
  <script src="/x-enkan/repl/enkan-repl.js"></script>
</body>
</html>
</#macro>
