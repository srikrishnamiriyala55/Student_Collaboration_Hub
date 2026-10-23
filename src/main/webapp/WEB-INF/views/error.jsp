<%@ page contentType="text/html; charset=UTF-8" %><%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<section class="empty"><p class="eyebrow">SOMETHING NEEDS YOUR ATTENTION</p><h1>Unable to complete that request</h1><p><c:out value="${error}"/></p><a class="button" href="${ctx}/dashboard">Return to workspace</a></section>
