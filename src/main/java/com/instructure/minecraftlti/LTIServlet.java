package com.instructure.minecraftlti;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.imsglobal.basiclti.BasicLTIUtil;
import org.imsglobal.basiclti.LtiLaunch;
import org.imsglobal.basiclti.LtiVerificationResult;

public class LTIServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private SecureRandom random = new SecureRandom();
  private final MinecraftLTI plugin;
  
  public LTIServlet(MinecraftLTI plugin) {
    this.plugin = plugin;
  }
  
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String consumerKey = request.getParameter("oauth_consumer_key");
    LTIConsumer consumer = plugin.getDatabase().find(LTIConsumer.class).where().eq("key", consumerKey).findUnique();
    if (consumer == null) {
      response.sendError(400, "Consumer key not valid");
      return;
    }
    LtiVerificationResult result = BasicLTIUtil.validateMessage(request, request.getRequestURL().toString(), consumer.getSecret().toString());
    if (!result.getSuccess()) {
      response.sendError(403, "Invalid message. Is your secret correct?");
      return;
    }
    
    LtiLaunch launch = result.getLtiLaunchResult();
    String toolId = launch.getToolConsumerInstanceGuid();
    String contextId = launch.getContextId();
    String userId = launch.getUser().getId();
    String sourcedid = request.getParameter("lis_result_sourcedid");
    String serviceUrl = request.getParameter("lis_outcome_service_url");
    String assignmentName = request.getParameter("custom_canvas_assignment_title");
    String xapiUrl = request.getParameter("custom_canvas_xapi_url");
    Boolean instructor = Arrays.asList(request.getParameter("roles").split(",")).contains("Instructor");
    
    HttpSession session = request.getSession();
    
    Assignment assignment = null;
    assignment = plugin.getDatabase().find(Assignment.class).where()
        .eq("name", assignmentName)
        .eq("contextId", contextId)
        .eq("toolId", toolId)
        .eq("consumerId", consumer.getId()).findUnique();
    if (assignment == null) {
      assignment = new Assignment(assignmentName, contextId, toolId, consumer);
      assignment.save();
    }
    
    User user = plugin.getDatabase().find(User.class).where()
        .eq("userId", userId)
        .eq("toolId", toolId)
        .eq("consumerId", consumer.getId()).findUnique();
    if (user == null) {
      String token = new BigInteger(130, random).toString(32);
      user = new User(userId, toolId, consumer, token);
    }
    
    user.setSourcedid(sourcedid);
    user.setServiceUrl(serviceUrl);
    user.setXapiUrl(xapiUrl);
    user.setInstructor(instructor);
    user.setAssignment(assignment);
    user.save();
    
    session.setAttribute("id", user.getId());
    response.sendRedirect("/token");
  }
}