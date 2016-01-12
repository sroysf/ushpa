<!DOCTYPE html>
<html>
<head>
  <#include "header.ftl">
</head>

<body>

  <#include "ushpa-nav.ftl">

<div class="jumbotron text-center">
  <div class="container">
    <a href="/" class="lang-logo">
      <img src="https://www.ushpa.aero/media/left_logo.jpg">
    </a>
    <h1>Docusign based USHPA Waivers</h1>
    <p>Welcome, ${fullName} (USHPA #${memberNumber})</p>
    <a type="button" class="btn btn-lg btn-default" href="/sign"><span class="glyphicon glyphicon-flash"></span> Sign Waiver</a>
    <a type="button" class="btn btn-lg btn-primary" href="https://github.com/heroku/java-getting-started"><span class="glyphicon glyphicon-download"></span> Source on GitHub</a>
  </div>
</div>
<div class="container">
  <div class="alert alert-info text-center" role="alert">
    The back-end of this application is written in Java, but it should be trivial to make the equivalent API calls in any language, such as Ruby.
  </div>
  <hr>
</div>


</body>
</html>
