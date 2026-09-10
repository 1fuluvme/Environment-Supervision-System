package com.neps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdminUserApiCheck {

    private static final String BASE = "http://localhost:8080";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEMO_PASSWORD = "DemoPass2026!";

    private static final String TEST_PUBLIC_PHONE =
            "18800000001";

    private static final String TEST_SECOND_PUBLIC_PHONE =
            "18800000002";

    private static final String TEST_GRID_PHONE =
            "18800000003";

    private static final String TEST_SECOND_GRID_PHONE =
            "18800000004";

    private static final String TEST_DECISION_PHONE =
            "18800000005";


    public static void main(String[] args) throws Exception {
        cleanupPreviousTestData();

        String adminPhone = env("ADMIN_PHONE");
        String adminPassword = env("ADMIN_PASSWORD");
        String publicPhone = TEST_PUBLIC_PHONE;
        String secondPublicPhone = TEST_SECOND_PUBLIC_PHONE;
        String gridPhone = TEST_GRID_PHONE;
        String decisionPhone = TEST_DECISION_PHONE;
        String secondPhone = TEST_SECOND_GRID_PHONE;

        HttpClient admin = newClient();
        HttpClient citizen = newClient();

        Map<String, String> gridRequest = Map.of(
                "phone", gridPhone,
                "displayName", "检查用网格员",
                "password", DEMO_PASSWORD,
                "role", "GRID");

        // 未登录，但携带有效 CSRF 令牌，应当被身份检查拒绝。
        postJson(admin, "/api/admin/users", gridRequest, 401);

        JsonNode publicUser = postJson(
                citizen, "/api/auth/register",
                Map.of(
                        "phone", publicPhone,
                        "displayName", "检查用公众",
                        "password", DEMO_PASSWORD,
                        "role", "ADMIN"),
                201);
        checkUser(publicUser, "PUBLIC");

        login(citizen, publicPhone, DEMO_PASSWORD);
        postJson(citizen, "/api/admin/users", gridRequest, 403);

        login(admin, adminPhone, adminPassword);

        JsonNode grid = postJson(
                admin, "/api/admin/users", gridRequest, 201);
        checkUser(grid, "GRID");

        JsonNode decision = postJson(
                admin, "/api/admin/users",
                Map.of(
                        "phone", decisionPhone,
                        "displayName", "检查用决策者",
                        "password", DEMO_PASSWORD,
                        "role", "DECISION"),
                201);
        checkUser(decision, "DECISION");

        postJson(admin, "/api/admin/users", gridRequest, 409);

        Map<String, String> invalidRole = new HashMap<>(gridRequest);
        invalidRole.put("role", "ADMIN");
        postJson(admin, "/api/admin/users", invalidRole, 400);

        System.out.println("管理员创建账号与权限检查全部通过");

        String lookupPath = "/api/admin/users?phone=" + gridPhone;
        String enabledPath =
                "/api/admin/users/" + grid.path("id").asLong() + "/enabled";

        JsonNode account = get(admin, lookupPath, 200);
        checkUser(account, "GRID");

        if (!account.path("enabled").asBoolean()) {
            throw new IllegalStateException("新建网格员应当处于启用状态");
        }

// 公众不能查询或修改其他账号。
        get(citizen, lookupPath, 403);
        postJson(citizen, enabledPath, Map.of("enabled", false), 403);

// 缺少 enabled 字段应当被拒绝。
        postJson(admin, enabledPath, Map.of(), 400);

// 先让网格员登录，随后测试这个已有会话。
        HttpClient gridClient = newClient();
        login(gridClient, gridPhone, DEMO_PASSWORD);
        get(gridClient, "/api/auth/me", 200);

        try {
            JsonNode disabled = postJson(
                    admin, enabledPath, Map.of("enabled", false), 200);

            if (!disabled.has("enabled")
                    || disabled.path("enabled").asBoolean()) {
                throw new IllegalStateException("停用后的响应状态不正确");
            }

            // 重复停用仍然成功。
            postJson(admin, enabledPath, Map.of("enabled", false), 200);

            JsonNode stored = get(admin, lookupPath, 200);
            if (!stored.has("enabled")
                    || stored.path("enabled").asBoolean()) {
                throw new IllegalStateException("账号停用状态未保存");
            }

            // 原来的登录会话应当被拒绝。
            get(gridClient, "/api/auth/me", 403);

            // 用新的会话重新登录，也应当失败。
            String form = "phone=" + gridPhone
                    + "&password="
                    + URLEncoder.encode(DEMO_PASSWORD, StandardCharsets.UTF_8);

            post(newClient(), "/api/auth/login",
                    "application/x-www-form-urlencoded", form, 401);
        } finally {
            // 即使检查中途失败，也尝试恢复这个测试账号。
            postJson(admin, enabledPath, Map.of("enabled", true), 200);
        }

// 重新启用后，可以重新登录。
        HttpClient restoredClient = newClient();
        login(restoredClient, gridPhone, DEMO_PASSWORD);
        get(restoredClient, "/api/auth/me", 200);

// 管理员不能通过本接口停用自己。
        JsonNode currentAdmin = get(admin, "/api/auth/me", 200);
        postJson(
                admin,
                "/api/admin/users/" + currentAdmin.path("id").asLong() + "/enabled",
                Map.of("enabled", false),
                403);

        System.out.println("账号查询、启停与会话状态检查全部通过");

        long insideGridId = Long.parseLong(env("TEST_INSIDE_GRID_ID"));
        long childGridId = Long.parseLong(env("TEST_CHILD_GRID_ID"));
        long outsideGridId = Long.parseLong(env("TEST_OUTSIDE_GRID_ID"));

        long workerId = grid.path("id").asLong();

        String assignBase = "/api/admin/users/" + workerId + "/grids/";
        String insidePath = assignBase + insideGridId;

// 未登录、公众、网格员都不能执行管理员分配操作。
        postJson(newClient(), insidePath, Map.of(), 401);
        postJson(citizen, insidePath, Map.of(), 403);
        postJson(restoredClient, insidePath, Map.of(), 403);

// 参数不合法。
        postJson(admin,
                "/api/admin/users/0/grids/" + insideGridId,
                Map.of(), 400);

// 公众不能成为负责网格的网格员。
        postJson(admin,
                "/api/admin/users/" + publicUser.path("id").asLong()
                        + "/grids/" + insideGridId,
                Map.of(), 400);

// 停用的网格员不能获得分配。
        postJson(admin, enabledPath, Map.of("enabled", false), 200);
        try {
            postJson(admin, insidePath, Map.of(), 400);
        } finally {
            postJson(admin, enabledPath, Map.of("enabled", true), 200);
        }

// 范围外的网格不能分配。
        postJson(admin, assignBase + outsideGridId, Map.of(), 403);

// 直接授权范围和下级区域都允许分配。
        postJson(admin, insidePath, Map.of(), 204);
        postJson(admin, insidePath, Map.of(), 204);
        postJson(admin, assignBase + childGridId, Map.of(), 204);

        System.out.println("网格分配、重复请求和区域权限检查通过");

        String minePath = "/api/grid/grids";

// 这个接口仅允许网格员访问。
        get(newClient(), minePath, 401);
        get(citizen, minePath, 403);
        get(admin, minePath, 403);

// 前一轮给第一个网格员分配了两个网格。
        checkIds(
                get(restoredClient, minePath, 200),
                insideGridId, childGridId);

        JsonNode secondWorker = postJson(
                admin, "/api/admin/users",
                Map.of(
                        "phone", secondPhone,
                        "displayName", "第二名检查网格员",
                        "password", DEMO_PASSWORD,
                        "role", "GRID"),
                201);
        checkUser(secondWorker, "GRID");

        HttpClient secondClient = newClient();
        login(secondClient, secondPhone, DEMO_PASSWORD);

// 没有分配记录时，应该返回空数组。
        checkIds(get(secondClient, minePath, 200));

        long secondId = secondWorker.path("id").asLong();
        String secondChildPath =
                "/api/admin/users/" + secondId + "/grids/" + childGridId;

        postJson(admin, secondChildPath, Map.of(), 204);
        checkIds(get(secondClient, minePath, 200), childGridId);

// 即使额外传入别人的 userId，仍只能查到自己负责的网格。
        checkIds(
                get(secondClient, minePath + "?userId=" + workerId, 200),
                childGridId);

// 未登录、公众和网格员不能撤销分配。
        delete(newClient(), insidePath, 401);
        delete(citizen, insidePath, 403);
        delete(restoredClient, insidePath, 403);

// 管理员不能撤销范围外网格的关联。
        delete(admin, assignBase + outsideGridId, 403);

// 管理员撤销第一个网格员的一号网格。
        delete(admin, insidePath, 204);
        delete(admin, insidePath, 204);

        checkIds(
                get(restoredClient, minePath, 200),
                childGridId);

// 停用账号后仍可清理它的分配。
        postJson(admin, enabledPath, Map.of("enabled", false), 200);
        try {
            get(restoredClient, minePath, 403);
            delete(admin, assignBase + childGridId, 204);
        } finally {
            postJson(admin, enabledPath, Map.of("enabled", true), 200);
        }

        HttpClient firstAgain = newClient();
        login(firstAgain, gridPhone, DEMO_PASSWORD);
        checkIds(get(firstAgain, minePath, 200));

// 第一个人的撤销不应影响第二个人。
        checkIds(get(secondClient, minePath, 200), childGridId);

        System.out.println("本人网格查询、数据隔离和撤销分配检查通过");

        // 从现有网格信息读取区域 ID，不再手工猜测编号。
        long insideRegionId = get(
                admin, "/api/grids/" + insideGridId, 200)
                .path("regionId").asLong();

        long childRegionId = get(
                admin, "/api/grids/" + childGridId, 200)
                .path("regionId").asLong();

        long outsideRegionId = get(
                admin, "/api/grids/" + outsideGridId, 200)
                .path("regionId").asLong();

        long decisionId = decision.path("id").asLong();

        String regionMinePath = "/api/decision/regions";
        String grantBase =
                "/api/admin/users/" + decisionId + "/regions/";

        HttpClient decisionClient = newClient();
        login(decisionClient, decisionPhone, DEMO_PASSWORD);

// 决策者查询接口的身份限制。
        get(newClient(), regionMinePath, 401);
        get(citizen, regionMinePath, 403);
        get(admin, regionMinePath, 403);
        get(secondClient, regionMinePath, 403);

// 新决策者尚未获得授权，应返回空数组。
        checkIds(get(decisionClient, regionMinePath, 200));

// 传入管理员 ID，也不能读取管理员的授权。
        checkIds(get(
                decisionClient,
                regionMinePath + "?userId=" + currentAdmin.path("id").asLong(),
                200));

// 只有管理员可以授权。
        postJson(newClient(), grantBase + insideRegionId, Map.of(), 401);
        postJson(citizen, grantBase + insideRegionId, Map.of(), 403);
        postJson(decisionClient, grantBase + insideRegionId, Map.of(), 403);

// 目标必须是决策者。
        postJson(admin,
                "/api/admin/users/" + workerId + "/regions/" + insideRegionId,
                Map.of(), 400);

// ID 校验与不存在的区域。
        postJson(admin, grantBase + "0", Map.of(), 400);
        postJson(admin, grantBase + Long.MAX_VALUE, Map.of(), 404);

// 停用决策者时，不能授权，也不能使用本人区域接口。
        String decisionEnabledPath =
                "/api/admin/users/" + decisionId + "/enabled";

        postJson(admin, decisionEnabledPath, Map.of("enabled", false), 200);
        try {
            postJson(admin, grantBase + insideRegionId, Map.of(), 400);
            get(decisionClient, regionMinePath, 403);
        } finally {
            postJson(admin, decisionEnabledPath, Map.of("enabled", true), 200);
        }

// 管理员不能越权授予其他区。
        postJson(admin, grantBase + outsideRegionId, Map.of(), 403);

// 先授予子区域，此时不能获得父区域权限。
        postJson(admin, grantBase + childRegionId, Map.of(), 204);
        checkIds(get(decisionClient, regionMinePath, 200), childRegionId);

// 再授予父区域，列表展开下级，且不重复。
        postJson(admin, grantBase + insideRegionId, Map.of(), 204);
        postJson(admin, grantBase + insideRegionId, Map.of(), 204);
        checkIds(
                get(decisionClient, regionMinePath, 200),
                insideRegionId, childRegionId);

        System.out.println("区域授权、下级继承与数据隔离检查通过");

        // 一、身份与参数检查。
        delete(newClient(), grantBase + insideRegionId, 401);
        delete(citizen, grantBase + insideRegionId, 403);
        delete(decisionClient, grantBase + insideRegionId, 403);
        delete(secondClient, grantBase + insideRegionId, 403);

        delete(admin, grantBase + "0", 400);
        delete(admin, grantBase + Long.MAX_VALUE, 404);

        delete(admin,
                "/api/admin/users/" + workerId + "/regions/" + insideRegionId,
                400);

// 即使指定授权不存在，也必须先检查管理员的区域权限。
        delete(admin, grantBase + outsideRegionId, 403);

// 上面的失败请求不应改变原有授权。
        checkIds(
                get(decisionClient, regionMinePath, 200),
                insideRegionId, childRegionId);

// 二、撤销子区域的直接授权，仍然可以从父区域继承。
        delete(admin, grantBase + childRegionId, 204);
        delete(admin, grantBase + childRegionId, 204);

        checkIds(
                get(decisionClient, regionMinePath, 200),
                insideRegionId, childRegionId);

// 三、恢复子区域直接授权，再撤销父区域。
// 子区域的独立授权应当保留。
        postJson(admin, grantBase + childRegionId, Map.of(), 204);

        delete(admin, grantBase + insideRegionId, 204);
        delete(admin, grantBase + insideRegionId, 204);

        checkIds(
                get(decisionClient, regionMinePath, 200),
                childRegionId);

// 四、停用决策者后，管理员仍然可以撤销剩余授权。
        postJson(admin, decisionEnabledPath, Map.of("enabled", false), 200);

        try {
            get(decisionClient, regionMinePath, 403);

            delete(admin, grantBase + childRegionId, 204);
            delete(admin, grantBase + childRegionId, 204);
        } finally {
            postJson(admin, decisionEnabledPath, Map.of("enabled", true), 200);
        }

// 重新登录后，没有剩余授权，应返回空数组。
        HttpClient decisionAfterRemoval = newClient();
        login(decisionAfterRemoval, decisionPhone, DEMO_PASSWORD);

        checkIds(get(decisionAfterRemoval, regionMinePath, 200));

        System.out.println("区域撤销、继承关系与停用账号清理检查通过");

        // 创建第二个公众账号，用于检查反馈数据隔离。
        HttpClient secondCitizen = newClient();

        JsonNode secondPublicUser = postJson(
                secondCitizen,
                "/api/auth/register",
                Map.of(
                        "phone", secondPublicPhone,
                        "displayName", "第二名检查用公众",
                        "password", DEMO_PASSWORD,
                        "role", "ADMIN"),
                201);

        checkUser(secondPublicUser, "PUBLIC");
        login(secondCitizen, secondPublicPhone, DEMO_PASSWORD);

        String feedbackPath = "/api/feedbacks";
        String observedAt = LocalDateTime.now()
                .minusMinutes(10)
                .withNano(0)
                .toString();

        Map<String, Object> feedbackRequest = Map.of(
                "gridId", insideGridId,
                "address", "演示区环保路12号附近",
                "observedAt", observedAt,
                "description", "现场有明显异味，并伴有少量烟尘。");

// 未登录以及非公众角色不能提交反馈。
        postJson(newClient(), feedbackPath, feedbackRequest, 401);
        postJson(admin, feedbackPath, feedbackRequest, 403);
        postJson(firstAgain, feedbackPath, feedbackRequest, 403);
        postJson(decisionAfterRemoval, feedbackPath, feedbackRequest, 403);

// 检查无效网格。
        Map<String, Object> zeroGridRequest =
                new HashMap<>(feedbackRequest);
        zeroGridRequest.put("gridId", 0);

        postJson(citizen, feedbackPath, zeroGridRequest, 400);

        Map<String, Object> missingGridRequest =
                new HashMap<>(feedbackRequest);
        missingGridRequest.put("gridId", Long.MAX_VALUE);

        postJson(citizen, feedbackPath, missingGridRequest, 404);

// 检查空地址。
        Map<String, Object> blankAddressRequest =
                new HashMap<>(feedbackRequest);
        blankAddressRequest.put("address", "   ");

        postJson(citizen, feedbackPath, blankAddressRequest, 400);

// 检查空描述。
        Map<String, Object> blankDescriptionRequest =
                new HashMap<>(feedbackRequest);
        blankDescriptionRequest.put("description", "   ");

        postJson(citizen, feedbackPath, blankDescriptionRequest, 400);

// 观测时间不能晚于当前时间。
        Map<String, Object> futureTimeRequest =
                new HashMap<>(feedbackRequest);
        futureTimeRequest.put(
                "observedAt",
                LocalDateTime.now()
                        .plusDays(1)
                        .withNano(0)
                        .toString());

        postJson(citizen, feedbackPath, futureTimeRequest, 400);

// 提交一条有效反馈。
        JsonNode createdFeedback = postJson(
                citizen,
                feedbackPath,
                feedbackRequest,
                201);

        checkFeedback(
                createdFeedback,
                insideGridId,
                "演示区环保路12号附近",
                "现场有明显异味，并伴有少量烟尘。");

        long feedbackId = createdFeedback.path("id").asLong();
        byte[] testPng = createTestPng();

        String attachmentListPath =
                "/api/feedbacks/"
                        + feedbackId
                        + "/attachments";

// 未登录和非公众角色不能上传。
        postMultipart(
                newClient(),
                attachmentListPath,
                "scene.png",
                "image/png",
                testPng,
                401);

        postMultipart(
                admin,
                attachmentListPath,
                "scene.png",
                "image/png",
                testPng,
                403);

// 其他公众不能向别人的反馈上传图片。
        postMultipart(
                secondCitizen,
                attachmentListPath,
                "scene.png",
                "image/png",
                testPng,
                404);

// 空文件无效。
        postMultipart(
                citizen,
                attachmentListPath,
                "empty.png",
                "image/png",
                new byte[0],
                400);

// 文件名是PNG，但内容是普通文本，也必须拒绝。
        postMultipart(
                citizen,
                attachmentListPath,
                "fake.png",
                "image/png",
                "这不是图片".getBytes(StandardCharsets.UTF_8),
                400);

// 声明为普通二进制文件，但实际内容是PNG。
// 后端应按实际内容识别为image/png。
        JsonNode uploadedAttachment = postMultipart(
                citizen,
                attachmentListPath,
                "scene.png",
                "application/octet-stream",
                testPng,
                201);

        long attachmentId =
                uploadedAttachment.path("id").asLong();

        String attachmentContentPath =
                "/api/attachments/"
                        + attachmentId
                        + "/content";

        boolean invalidAttachment =
                attachmentId <= 0
                        || !"scene.png".equals(
                        uploadedAttachment
                                .path("originalName")
                                .asText())
                        || !"image/png".equals(
                        uploadedAttachment
                                .path("contentType")
                                .asText())
                        || uploadedAttachment
                        .path("sizeBytes")
                        .asLong() != testPng.length
                        || !attachmentContentPath.equals(
                        uploadedAttachment
                                .path("contentUrl")
                                .asText())
                        || uploadedAttachment.has("storagePath")
                        || uploadedAttachment.has("uploaderId");

        if (invalidAttachment) {
            throw new IllegalStateException(
                    "附件响应不符合预期："
                            + uploadedAttachment);
        }

// 反馈本人可以查看列表和下载。
        requireRecordId(
                get(citizen, attachmentListPath, 200),
                attachmentId,
                true);

        byte[] ownerDownload = getBytes(
                citizen,
                attachmentContentPath,
                200);

        if (!Arrays.equals(testPng, ownerDownload)) {
            throw new IllegalStateException(
                    "反馈本人下载到的图片内容不一致");
        }

// 授权区域内的管理员可以查看。
        requireRecordId(
                get(admin, attachmentListPath, 200),
                attachmentId,
                true);

        byte[] adminDownload = getBytes(
                admin,
                attachmentContentPath,
                200);

        if (!Arrays.equals(testPng, adminDownload)) {
            throw new IllegalStateException(
                    "管理员下载到的图片内容不一致");
        }

// 其他公众不能读取。
        get(secondCitizen, attachmentListPath, 404);
        getBytes(secondCitizen, attachmentContentPath, 404);

// 决策者没有附件读取权限。
        get(decisionAfterRemoval, attachmentListPath, 403);
        getBytes(
                decisionAfterRemoval,
                attachmentContentPath,
                403);

// 此时任务尚未指派，网格员不能读取。
        get(firstAgain, attachmentListPath, 404);
        getBytes(firstAgain, attachmentContentPath, 404);

        System.out.println(
                "图片格式、上传和反馈本人附件权限检查通过");

// 本人的列表只能包含刚提交的反馈。
        checkIds(
                get(citizen, "/api/feedbacks/mine", 200),
                feedbackId);

// 伪造 userId 查询参数也不能改变查询对象。
        checkIds(
                get(
                        citizen,
                        "/api/feedbacks/mine?userId="
                                + secondPublicUser.path("id").asLong(),
                        200),
                feedbackId);

// 第二名公众看不到第一名公众的列表记录。
        checkIds(
                get(secondCitizen, "/api/feedbacks/mine", 200));

// 本人可以查看详情。
        JsonNode feedbackDetail = get(
                citizen,
                "/api/feedbacks/" + feedbackId,
                200);

        checkFeedback(
                feedbackDetail,
                insideGridId,
                "演示区环保路12号附近",
                "现场有明显异味，并伴有少量烟尘。");

// 第二名公众即使猜中反馈ID，也只能得到404。
        get(
                secondCitizen,
                "/api/feedbacks/" + feedbackId,
                404);

// 未登录及其他角色不能读取公众反馈详情。
        get(newClient(), "/api/feedbacks/" + feedbackId, 401);
        get(admin, "/api/feedbacks/" + feedbackId, 403);
        get(firstAgain, "/api/feedbacks/" + feedbackId, 403);

// 详情接口的ID校验。
        get(citizen, "/api/feedbacks/0", 400);
        get(
                citizen,
                "/api/feedbacks/" + Long.MAX_VALUE,
                404);

        System.out.println("公众反馈提交、输入校验和数据隔离检查通过");

        // 前面的网格撤销测试已经清除了第一名网格员的网格，
// 现在重新给他分配反馈所在的网格。
        postJson(
                admin,
                assignBase + insideGridId,
                Map.of(),
                204);

        String taskAssignPath =
                "/api/admin/feedbacks/"
                        + feedbackId
                        + "/assign";

        Map<String, Object> taskRequest = Map.of(
                "assigneeId", workerId,
                "requirement", "请到现场核查异味来源并拍照记录",
                "priority", "HIGH");

// 管理员指派反馈。
        JsonNode assignedTask = postJson(
                admin,
                taskAssignPath,
                taskRequest,
                200);

        checkInspectionTask(
                assignedTask,
                feedbackId,
                insideGridId,
                workerId);

// 网格员应当看到刚分配给自己的任务。
        JsonNode workerTasks = get(
                firstAgain,
                "/api/tasks/mine",
                200);

        if (!workerTasks.isArray()
                || workerTasks.size() != 1) {
            throw new IllegalStateException(
                    "网格员任务列表不符合预期：" + workerTasks);
        }

        checkInspectionTask(
                workerTasks.get(0),
                feedbackId,
                insideGridId,
                workerId);

// 公众查询反馈时应看到状态已经变成CHECKING。
        JsonNode assignedFeedback = get(
                citizen,
                "/api/feedbacks/" + feedbackId,
                200);

        if (!"CHECKING".equals(
                assignedFeedback.path("status").asText())) {
            throw new IllegalStateException(
                    "指派后反馈应进入CHECKING状态："
                            + assignedFeedback);
        }

        // 当前负责人可以查看和下载任务图片。
        requireRecordId(
                get(firstAgain, attachmentListPath, 200),
                attachmentId,
                true);

        byte[] workerDownload = getBytes(
                firstAgain,
                attachmentContentPath,
                200);

        if (!Arrays.equals(testPng, workerDownload)) {
            throw new IllegalStateException(
                    "当前负责人下载到的图片内容不一致");
        }

// 反馈已经进入CHECKING，公众不能继续添加图片。
        postMultipart(
                citizen,
                attachmentListPath,
                "late.png",
                "image/png",
                testPng,
                409);

        System.out.println("管理员指派与网格员本人任务查询通过");

        long originalTaskId =
                assignedTask.path("taskId").asLong();

// 一、只有管理员可以指派。
        postJson(newClient(), taskAssignPath, taskRequest, 401);
        postJson(citizen, taskAssignPath, taskRequest, 403);
        postJson(firstAgain, taskAssignPath, taskRequest, 403);
        postJson(decisionAfterRemoval, taskAssignPath, taskRequest, 403);

// 本人任务接口只允许网格员访问。
        get(newClient(), "/api/tasks/mine", 401);
        get(citizen, "/api/tasks/mine", 403);
        get(admin, "/api/tasks/mine", 403);
        get(decisionAfterRemoval, "/api/tasks/mine", 403);

// 二、重复提交完全相同的指派请求。
// 应返回同一个任务，不应生成第二条任务。
        JsonNode repeatedTask = postJson(
                admin,
                taskAssignPath,
                taskRequest,
                200);

        checkInspectionTask(
                repeatedTask,
                feedbackId,
                insideGridId,
                workerId);

        if (repeatedTask.path("taskId").asLong()
                != originalTaskId) {
            throw new IllegalStateException(
                    "重复指派生成了新的核查任务");
        }

// 三、检查反馈ID和请求参数。
        postJson(
                admin,
                "/api/admin/feedbacks/0/assign",
                taskRequest,
                400);

        postJson(
                admin,
                "/api/admin/feedbacks/"
                        + Long.MAX_VALUE
                        + "/assign",
                taskRequest,
                404);

        postJson(
                admin,
                taskAssignPath,
                Map.of(
                        "assigneeId", 0,
                        "requirement", "现场核查",
                        "priority", "HIGH"),
                400);

        postJson(
                admin,
                taskAssignPath,
                Map.of(
                        "assigneeId", workerId,
                        "requirement", "   ",
                        "priority", "HIGH"),
                400);

        postJson(
                admin,
                taskAssignPath,
                Map.of(
                        "assigneeId", workerId,
                        "requirement", "现场核查",
                        "priority", "URGENT"),
                400);

// 四、不能把任务指派给公众或决策者。
        postJson(
                admin,
                taskAssignPath,
                Map.of(
                        "assigneeId",
                        publicUser.path("id").asLong(),
                        "requirement", "现场核查",
                        "priority", "HIGH"),
                400);

        postJson(
                admin,
                taskAssignPath,
                Map.of(
                        "assigneeId", decisionId,
                        "requirement", "现场核查",
                        "priority", "HIGH"),
                400);

// 第二名网格员目前只负责子网格，
// 尚未负责当前反馈所在网格，因此不能接收任务。
        Map<String, Object> secondWorkerTaskRequest = Map.of(
                "assigneeId", secondId,
                "requirement", "请到现场核查异味来源并拍照记录",
                "priority", "HIGH");

        postJson(
                admin,
                taskAssignPath,
                secondWorkerTaskRequest,
                400);

// 五、停用的网格员不能接收任务。
        String secondWorkerEnabledPath =
                "/api/admin/users/"
                        + secondId
                        + "/enabled";

        postJson(
                admin,
                secondWorkerEnabledPath,
                Map.of("enabled", false),
                200);

        try {
            postJson(
                    admin,
                    taskAssignPath,
                    secondWorkerTaskRequest,
                    400);

            get(secondClient, "/api/tasks/mine", 403);
        } finally {
            postJson(
                    admin,
                    secondWorkerEnabledPath,
                    Map.of("enabled", true),
                    200);
        }

// 六、管理员不能指派自己区域范围外的反馈。
        Map<String, Object> outsideFeedbackRequest =
                new HashMap<>(feedbackRequest);

        outsideFeedbackRequest.put(
                "gridId",
                outsideGridId);

        JsonNode outsideFeedback = postJson(
                citizen,
                feedbackPath,
                outsideFeedbackRequest,
                201);

        long outsideFeedbackId =
                outsideFeedback.path("id").asLong();

        postJson(
                admin,
                "/api/admin/feedbacks/"
                        + outsideFeedbackId
                        + "/assign",
                taskRequest,
                403);

// 范围外指派失败后，反馈仍应保持待指派状态。
        JsonNode unchangedOutsideFeedback = get(
                citizen,
                "/api/feedbacks/" + outsideFeedbackId,
                200);

        if (!"PENDING_ASSIGN".equals(
                unchangedOutsideFeedback
                        .path("status")
                        .asText())) {
            throw new IllegalStateException(
                    "越权指派失败后反馈状态被意外修改："
                            + unchangedOutsideFeedback);
        }

// 七、让第二名网格员也负责当前反馈所在网格。
        String secondInsideGridPath =
                "/api/admin/users/"
                        + secondId
                        + "/grids/"
                        + insideGridId;

        postJson(
                admin,
                secondInsideGridPath,
                Map.of(),
                204);

// 现在可以将任务从第一名网格员改派给第二名网格员。
        JsonNode reassignedTask = postJson(
                admin,
                taskAssignPath,
                secondWorkerTaskRequest,
                200);

        checkInspectionTask(
                reassignedTask,
                feedbackId,
                insideGridId,
                secondId);

// 改派只能更新原任务，任务ID不能改变。
        if (reassignedTask.path("taskId").asLong()
                != originalTaskId) {
            throw new IllegalStateException(
                    "改派错误地创建了新的核查任务");
        }

// 原负责人应立即看不到该任务。
        JsonNode formerWorkerTasks = get(
                firstAgain,
                "/api/tasks/mine",
                200);

        if (!formerWorkerTasks.isArray()
                || formerWorkerTasks.size() != 0) {
            throw new IllegalStateException(
                    "改派后原负责人仍能看到任务："
                            + formerWorkerTasks);
        }

// 新负责人应当看到该任务。
        JsonNode newWorkerTasks = get(
                secondClient,
                "/api/tasks/mine",
                200);

        if (!newWorkerTasks.isArray()
                || newWorkerTasks.size() != 1) {
            throw new IllegalStateException(
                    "新负责人任务列表不符合预期："
                            + newWorkerTasks);
        }

        checkInspectionTask(
                newWorkerTasks.get(0),
                feedbackId,
                insideGridId,
                secondId);

        // 原负责人已经失去附件读取权限。
        get(firstAgain, attachmentListPath, 404);
        getBytes(firstAgain, attachmentContentPath, 404);

        // 新负责人获得附件读取权限。
        requireRecordId(
                get(secondClient, attachmentListPath, 200),
                attachmentId,
                true);

        byte[] newWorkerDownload = getBytes(
                secondClient,
                attachmentContentPath,
                200);

        if (!Arrays.equals(testPng, newWorkerDownload)) {
            throw new IllegalStateException(
                    "改派后的负责人下载图片内容不一致");
        }

        System.out.println(
                "附件权限随任务改派正确转移");

// 对改派结果重复提交，仍然必须返回同一任务。
        JsonNode repeatedReassignment = postJson(
                admin,
                taskAssignPath,
                secondWorkerTaskRequest,
                200);

        if (repeatedReassignment
                .path("taskId")
                .asLong() != originalTaskId) {

            throw new IllegalStateException(
                    "重复改派生成了新的核查任务");
        }

        System.out.println(
                "指派权限、重复请求、区域限制与改派检查通过");

        String adminFeedbackPath = "/api/admin/feedbacks";

// 一、只有管理员可以使用后台反馈查询。
        get(newClient(), adminFeedbackPath, 401);
        get(citizen, adminFeedbackPath, 403);
        get(firstAgain, adminFeedbackPath, 403);
        get(decisionAfterRemoval, adminFeedbackPath, 403);

// 二、不带筛选条件时：
// 能看到授权范围内的反馈，不能看到范围外反馈。
        JsonNode allAccessibleFeedbacks = get(
                admin,
                adminFeedbackPath,
                200);

        requireRecordId(
                allAccessibleFeedbacks,
                feedbackId,
                true);

        requireRecordId(
                allAccessibleFeedbacks,
                outsideFeedbackId,
                false);

// 三、按状态筛选。
// 服务层会将小写checking转换为CHECKING。
        JsonNode checkingFeedbacks = get(
                admin,
                adminFeedbackPath + "?status=checking",
                200);

        requireRecordId(
                checkingFeedbacks,
                feedbackId,
                true);

        JsonNode pendingFeedbacks = get(
                admin,
                adminFeedbackPath + "?status=PENDING_ASSIGN",
                200);

        requireRecordId(
                pendingFeedbacks,
                feedbackId,
                false);

// 不支持的状态应返回400。
        get(
                admin,
                adminFeedbackPath + "?status=UNKNOWN",
                400);

// 四、按区域筛选。
// insideRegionId应当包含当前反馈。
        JsonNode insideRegionFeedbacks = get(
                admin,
                adminFeedbackPath
                        + "?regionId="
                        + insideRegionId,
                200);

        requireRecordId(
                insideRegionFeedbacks,
                feedbackId,
                true);

// 当前反馈属于父区域，不属于childRegionId的下级，
// 所以按子区域筛选时不应返回。
        JsonNode childRegionFeedbacks = get(
                admin,
                adminFeedbackPath
                        + "?regionId="
                        + childRegionId,
                200);

        requireRecordId(
                childRegionFeedbacks,
                feedbackId,
                false);

// 无效区域ID。
        get(
                admin,
                adminFeedbackPath + "?regionId=0",
                400);

// 管理员不能查询授权范围外的区域。
        get(
                admin,
                adminFeedbackPath
                        + "?regionId="
                        + outsideRegionId,
                403);

// 五、按创建时间筛选。
        LocalDateTime feedbackCreatedAt =
                LocalDateTime.parse(
                        createdFeedback
                                .path("createdAt")
                                .asText());

        String fromValue = URLEncoder.encode(
                feedbackCreatedAt
                        .minusSeconds(1)
                        .toString(),
                StandardCharsets.UTF_8);

        String toValue = URLEncoder.encode(
                feedbackCreatedAt
                        .plusSeconds(1)
                        .toString(),
                StandardCharsets.UTF_8);

        JsonNode timeFilteredFeedbacks = get(
                admin,
                adminFeedbackPath
                        + "?from=" + fromValue
                        + "&to=" + toValue,
                200);

        requireRecordId(
                timeFilteredFeedbacks,
                feedbackId,
                true);

// 开始时间晚于结束时间，应返回400。
        get(
                admin,
                adminFeedbackPath
                        + "?from=" + toValue
                        + "&to=" + fromValue,
                400);

// 从明天开始查询，不应包含今天创建的反馈。
        String tomorrowValue = URLEncoder.encode(
                LocalDateTime.now()
                        .plusDays(1)
                        .withNano(0)
                        .toString(),
                StandardCharsets.UTF_8);

        JsonNode futureFeedbacks = get(
                admin,
                adminFeedbackPath
                        + "?from=" + tomorrowValue,
                200);

        requireRecordId(
                futureFeedbacks,
                feedbackId,
                false);

// 六、组合筛选。
        JsonNode combinedFeedbacks = get(
                admin,
                adminFeedbackPath
                        + "?status=CHECKING"
                        + "&regionId=" + insideRegionId
                        + "&from=" + fromValue
                        + "&to=" + toValue,
                200);

        requireRecordId(
                combinedFeedbacks,
                feedbackId,
                true);

// 七、管理员详情查询。
        JsonNode adminFeedbackDetail = get(
                admin,
                adminFeedbackPath + "/" + feedbackId,
                200);

        boolean invalidAdminDetail =
                adminFeedbackDetail.path("id").asLong()
                        != feedbackId
                        || adminFeedbackDetail.path("gridId").asLong()
                        != insideGridId
                        || !publicPhone.equals(
                        adminFeedbackDetail
                                .path("submitterPhone")
                                .asText())
                        || !"CHECKING".equals(
                        adminFeedbackDetail
                                .path("status")
                                .asText())
                        || !isAnalysisLifecycleStatus(
                        adminFeedbackDetail
                                .path("analysisStatus")
                                .asText())
                        || adminFeedbackDetail.path("taskId").asLong()
                        != originalTaskId
                        || adminFeedbackDetail.path("assigneeId").asLong()
                        != secondId
                        || !"PENDING".equals(
                        adminFeedbackDetail
                                .path("taskStatus")
                                .asText())
                        || !"HIGH".equals(
                        adminFeedbackDetail
                                .path("priority")
                                .asText())
                        || adminFeedbackDetail.has("password")
                        || adminFeedbackDetail.has("passwordHash")
                        || adminFeedbackDetail.has("storagePath");

        if (invalidAdminDetail) {
            throw new IllegalStateException(
                    "管理员反馈详情不符合预期："
                            + adminFeedbackDetail);
        }

// 八、详情接口的身份、ID和区域限制。
        get(
                newClient(),
                adminFeedbackPath + "/" + feedbackId,
                401);

        get(
                citizen,
                adminFeedbackPath + "/" + feedbackId,
                403);

        get(
                firstAgain,
                adminFeedbackPath + "/" + feedbackId,
                403);

        get(
                decisionAfterRemoval,
                adminFeedbackPath + "/" + feedbackId,
                403);

        get(
                admin,
                adminFeedbackPath + "/0",
                400);

        get(
                admin,
                adminFeedbackPath + "/" + Long.MAX_VALUE,
                404);

// 反馈确实存在，但位于管理员授权范围之外。
        get(
                admin,
                adminFeedbackPath + "/" + outsideFeedbackId,
                403);

        System.out.println(
                "管理员反馈状态、区域、时间筛选与详情权限检查通过");

// 检测提交、AQI计算和状态流转检查
        String measurementPath =
                "/api/tasks/"
                        + originalTaskId
                        + "/measurements";

        Map<String, Object> validMeasurement =
                measurementRequest(
                        "DAILY",
                        true,
                        null);

// 一、接口权限检查。
        postJson(
                newClient(),
                measurementPath,
                validMeasurement,
                401);

        postJson(
                citizen,
                measurementPath,
                validMeasurement,
                403);

        postJson(
                admin,
                measurementPath,
                validMeasurement,
                403);

        postJson(
                decisionAfterRemoval,
                measurementPath,
                validMeasurement,
                403);

// 改派后的原负责人不能提交这个任务。
        postJson(
                firstAgain,
                measurementPath,
                validMeasurement,
                404);

// 非法任务ID。
        postJson(
                secondClient,
                "/api/tasks/0/measurements",
                validMeasurement,
                400);

        System.out.println(
                "检测提交的登录、角色和任务归属检查通过");

// 二、请求参数检查。
        Map<String, Object> wrongReportType =
                new HashMap<>(validMeasurement);

        wrongReportType.put("reportType", "WEEKLY");

        postJson(
                secondClient,
                measurementPath,
                wrongReportType,
                400);

        Map<String, Object> futureMeasurement =
                new HashMap<>(validMeasurement);

        futureMeasurement.put(
                "measuredAt",
                LocalDateTime.now()
                        .plusDays(1)
                        .withNano(0)
                        .toString());

        postJson(
                secondClient,
                measurementPath,
                futureMeasurement,
                400);

        Map<String, Object> allMissing =
                new HashMap<>(validMeasurement);

        allMissing.put("so2", null);
        allMissing.put("no2", null);
        allMissing.put("co", null);
        allMissing.put("o3", null);
        allMissing.put("pm10", null);
        allMissing.put("pm25", null);
        allMissing.put(
                "missingReason",
                "检测仪器暂时不可用");

        postJson(
                secondClient,
                measurementPath,
                allMissing,
                400);

        Map<String, Object> missingWithoutReason =
                new HashMap<>(validMeasurement);

        missingWithoutReason.put("pm25", null);
        missingWithoutReason.put("missingReason", null);

        postJson(
                secondClient,
                measurementPath,
                missingWithoutReason,
                400);

        Map<String, Object> invalidWithoutReason =
                new HashMap<>(validMeasurement);

        invalidWithoutReason.put(
                "statisticallyValid",
                false);

        invalidWithoutReason.put(
                "invalidReason",
                null);

        postJson(
                secondClient,
                measurementPath,
                invalidWithoutReason,
                400);

        System.out.println(
                "检测时间、统计时段、缺测和有效性参数检查通过");

// 三、新建第二条反馈，用来测试瞬时读数。
        String instantAddress =
                "检查用瞬时检测位置";

        String instantDescription =
                "用于验证瞬时读数不能生成正式AQI";

        JsonNode instantFeedback = postJson(
                secondCitizen,
                "/api/feedbacks",
                Map.of(
                        "gridId", insideGridId,
                        "address", instantAddress,
                        "observedAt",
                        LocalDateTime.now()
                                .minusMinutes(30)
                                .withNano(0)
                                .toString(),
                        "description", instantDescription),
                201);

        long instantFeedbackId =
                instantFeedback.path("id").asLong();

        JsonNode instantTask = postJson(
                admin,
                "/api/admin/feedbacks/"
                        + instantFeedbackId
                        + "/assign",
                Map.of(
                        "assigneeId", secondId,
                        "requirement",
                        "进行现场瞬时检测并记录情况",
                        "priority", "MEDIUM"),
                200);

        long instantTaskId =
                instantTask.path("taskId").asLong();

        String instantMeasurementPath =
                "/api/tasks/"
                        + instantTaskId
                        + "/measurements";

        Map<String, Object> instantRequest =
                measurementRequest(
                        "INSTANT",
                        true,
                        null);

        JsonNode instantResult = postJson(
                secondClient,
                instantMeasurementPath,
                instantRequest,
                201);

        checkInstantMeasurement(
                instantResult,
                instantTaskId,
                instantFeedbackId);

        checkRuleResult(
                instantResult,
                "QUALITY_ISSUE",
                null,
                "数据质量");

        System.out.println(
                "瞬时读数保存和禁止生成正式AQI检查通过");

// 四、提交正式日报检测数据。
        JsonNode measurement = postJson(
                secondClient,
                measurementPath,
                validMeasurement,
                201);

        long measurementId =
                measurement.path("id").asLong();

        checkDailyMeasurement(
                measurement,
                originalTaskId,
                feedbackId,
                1);

        checkRuleResult(
                measurement,
                "NORMAL",
                null,
                "未触发异常规则");

// 同一个待复核任务不能重复提交。
        postJson(
                secondClient,
                measurementPath,
                validMeasurement,
                409);

// 原负责人仍然不能提交。
        postJson(
                firstAgain,
                measurementPath,
                validMeasurement,
                404);

        System.out.println(
                "日报AQI计算、首要污染物和重复提交检查通过");

// 五、检查任务状态。
        JsonNode workerTasksAfterMeasurement = get(
                secondClient,
                "/api/tasks/mine",
                200);

        JsonNode submittedTask = null;

        for (JsonNode item : workerTasksAfterMeasurement) {
            if (item.path("taskId").asLong()
                    == originalTaskId) {

                submittedTask = item;
                break;
            }
        }

        if (submittedTask == null) {
            throw new IllegalStateException(
                    "提交检测后找不到原核查任务");
        }

        if (!"PENDING_REVIEW".equals(
                submittedTask.path("taskStatus").asText())
                || !"PENDING_REVIEW".equals(
                submittedTask.path("feedbackStatus").asText())) {

            throw new IllegalStateException(
                    "检测提交后的任务状态不正确："
                            + submittedTask);
        }

// 六、公众应看到反馈进入待复核。
        JsonNode feedbackAfterMeasurement = get(
                citizen,
                "/api/feedbacks/" + feedbackId,
                200);

        if (!"PENDING_REVIEW".equals(
                feedbackAfterMeasurement
                        .path("status")
                        .asText())) {

            throw new IllegalStateException(
                    "检测提交后公众反馈未进入待复核："
                            + feedbackAfterMeasurement);
        }

// 七、管理员详情也应显示待复核状态。
        JsonNode adminAfterMeasurement = get(
                admin,
                adminFeedbackPath + "/" + feedbackId,
                200);

        if (!"PENDING_REVIEW".equals(
                adminAfterMeasurement.path("status").asText())
                || !"PENDING_REVIEW".equals(
                adminAfterMeasurement
                        .path("taskStatus")
                        .asText())) {

            throw new IllegalStateException(
                    "管理员反馈详情中的待复核状态不正确："
                            + adminAfterMeasurement);
        }
// 管理员检测查询与复核检查


        long instantMeasurementId =
                instantResult.path("id").asLong();

        String adminMeasurementPath =
                "/api/admin/measurements";

// 一、检测记录查询权限。
        get(
                newClient(),
                adminMeasurementPath,
                401);

        get(
                citizen,
                adminMeasurementPath,
                403);

        get(
                secondClient,
                adminMeasurementPath,
                403);

        get(
                decisionAfterRemoval,
                adminMeasurementPath,
                403);

// 管理员能够查询授权范围内的检测记录。
        JsonNode pendingMeasurements = get(
                admin,
                adminMeasurementPath
                        + "?reviewStatus=PENDING",
                200);

        requireRecordId(
                pendingMeasurements,
                measurementId,
                true);

        requireRecordId(
                pendingMeasurements,
                instantMeasurementId,
                true);

// 状态值错误。
        get(
                admin,
                adminMeasurementPath
                        + "?reviewStatus=UNKNOWN",
                400);

// 检测详情权限和ID校验。
        String measurementDetailPath =
                adminMeasurementPath
                        + "/"
                        + measurementId;

        get(
                newClient(),
                measurementDetailPath,
                401);

        get(
                citizen,
                measurementDetailPath,
                403);

        get(
                secondClient,
                measurementDetailPath,
                403);

        get(
                decisionAfterRemoval,
                measurementDetailPath,
                403);

        JsonNode measurementDetail = get(
                admin,
                measurementDetailPath,
                200);

        checkDailyMeasurement(
                measurementDetail,
                originalTaskId,
                feedbackId,
                1);

        get(
                admin,
                adminMeasurementPath + "/0",
                400);

        get(
                admin,
                adminMeasurementPath
                        + "/"
                        + Long.MAX_VALUE,
                404);

        String firstReviewPath =
                measurementDetailPath + "/review";

// 二、复核接口权限。
        postJson(
                newClient(),
                firstReviewPath,
                Map.of(
                        "decision", "RETURN",
                        "opinion", "请补充检测"),
                401);

        postJson(
                citizen,
                firstReviewPath,
                Map.of(
                        "decision", "RETURN",
                        "opinion", "请补充检测"),
                403);

        postJson(
                secondClient,
                firstReviewPath,
                Map.of(
                        "decision", "RETURN",
                        "opinion", "请补充检测"),
                403);

        postJson(
                decisionAfterRemoval,
                firstReviewPath,
                Map.of(
                        "decision", "RETURN",
                        "opinion", "请补充检测"),
                403);

// 非法记录ID。
        postJson(
                admin,
                adminMeasurementPath + "/0/review",
                Map.of(
                        "decision", "RETURN",
                        "opinion", "请补充检测"),
                400);

        postJson(
                admin,
                adminMeasurementPath
                        + "/"
                        + Long.MAX_VALUE
                        + "/review",
                Map.of(
                        "decision", "RETURN",
                        "opinion", "请补充检测"),
                404);

// decision只能是RETURN或COMPLETE。
        postJson(
                admin,
                firstReviewPath,
                Map.of(
                        "decision", "APPROVE",
                        "opinion", "错误决定值"),
                400);

// opinion不能为空。
        postJson(
                admin,
                firstReviewPath,
                Map.of(
                        "decision", "RETURN",
                        "opinion", ""),
                400);

// 普通结案必须填写公众可见说明。
        postJson(
                admin,
                firstReviewPath,
                Map.of(
                        "decision", "COMPLETE",
                        "opinion", "检测结果可以通过"),
                400);

        // 三、管理员退回第1版检测。
        String returnOpinion =
                "PM10与SO2处于并列边界，请补充现场说明后重新提交";

        JsonNode returnedReview = postJson(
                admin,
                firstReviewPath,
                Map.of(
                        "decision", "RETURN",
                        "opinion", returnOpinion),
                200);

        checkReviewResponse(
                returnedReview,
                measurementId,
                "RETURN",
                "RETURNED",
                "PENDING",
                "CHECKING");

// 同一条检测记录不能重复复核。
        postJson(
                admin,
                firstReviewPath,
                Map.of(
                        "decision", "RETURN",
                        "opinion", "重复退回"),
                409);

// 第1版已经进入RETURNED。
        JsonNode returnedMeasurement = get(
                admin,
                measurementDetailPath,
                200);

        if (!"RETURNED".equals(
                returnedMeasurement
                        .path("reviewStatus")
                        .asText())) {

            throw new IllegalStateException(
                    "退回后的检测状态不正确："
                            + returnedMeasurement);
        }

// 反馈回到核查中，公众办理说明仍为空。
        JsonNode checkingFeedback = get(
                citizen,
                "/api/feedbacks/" + feedbackId,
                200);

        if (!"CHECKING".equals(
                checkingFeedback.path("status").asText())
                || !checkingFeedback
                .path("publicReply")
                .isNull()) {

            throw new IllegalStateException(
                    "退回后的公众反馈状态不正确："
                            + checkingFeedback);
        }

// 任务回到PENDING，原负责人仍然不能看到。
        JsonNode currentWorkerTasks = get(
                secondClient,
                "/api/tasks/mine",
                200);

        requireTaskState(
                currentWorkerTasks,
                originalTaskId,
                "PENDING",
                "CHECKING");

        JsonNode formerWorkerAfterReturn = get(
                firstAgain,
                "/api/tasks/mine",
                200);

        requireTaskAbsent(
                formerWorkerAfterReturn,
                originalTaskId);

// 网格员提交第2版检测。
        Map<String, Object> revisedMeasurement =
                measurementRequest(
                        "DAILY",
                        true,
                        null);

        revisedMeasurement.put(
                "siteNote",
                "根据退回意见重新核查，已补充异味来源和现场照片说明");

        revisedMeasurement.put(
                "measuredAt",
                LocalDateTime.now()
                        .minusMinutes(2)
                        .withNano(0)
                        .toString());

        JsonNode secondVersion = postJson(
                secondClient,
                measurementPath,
                revisedMeasurement,
                201);

        long secondMeasurementId =
                secondVersion.path("id").asLong();

        checkDailyMeasurement(
                secondVersion,
                originalTaskId,
                feedbackId,
                2);

// 第1版不能因为出现第2版而丢失。
        JsonNode firstVersionAfterResubmit = get(
                admin,
                measurementDetailPath,
                200);

        if (!"RETURNED".equals(
                firstVersionAfterResubmit
                        .path("reviewStatus")
                        .asText())
                || firstVersionAfterResubmit
                .path("versionNo").asInt() != 1) {

            throw new IllegalStateException(
                    "重新提交后第1版历史被破坏："
                            + firstVersionAfterResubmit);
        }

// 第2版重新进入待复核。
        requireTaskState(
                get(secondClient, "/api/tasks/mine", 200),
                originalTaskId,
                "PENDING_REVIEW",
                "PENDING_REVIEW");

// 即使任务重新进入待复核，第1版仍不能再次复核。
        postJson(
                admin,
                firstReviewPath,
                Map.of(
                        "decision", "COMPLETE",
                        "opinion", "错误地复核旧版本",
                        "publicReply", "不应保存"),
                409);

        // 四、管理员通过第2版并普通结案。
        String secondReviewPath =
                adminMeasurementPath
                        + "/"
                        + secondMeasurementId
                        + "/review";

        String completedReply =
                "工作人员已完成现场检测，未发现需要进一步处置的污染问题。";

        JsonNode completedReview = postJson(
                admin,
                secondReviewPath,
                Map.of(
                        "decision", "COMPLETE",
                        "opinion",
                        "补充材料完整，检测数据符合要求，同意普通结案",
                        "publicReply",
                        completedReply),
                200);

        checkReviewResponse(
                completedReview,
                secondMeasurementId,
                "COMPLETE",
                "APPROVED",
                "COMPLETED",
                "COMPLETED");

// 已经通过的版本不能重复复核。
        postJson(
                admin,
                secondReviewPath,
                Map.of(
                        "decision", "COMPLETE",
                        "opinion", "重复通过",
                        "publicReply", completedReply),
                409);

// 结案后网格员不能继续提交第3版。
        postJson(
                secondClient,
                measurementPath,
                revisedMeasurement,
                409);

// 公众应当看到结案状态和办理说明。
        JsonNode completedFeedback = get(
                citizen,
                "/api/feedbacks/" + feedbackId,
                200);

        if (!"COMPLETED".equals(
                completedFeedback.path("status").asText())
                || !completedReply.equals(
                completedFeedback
                        .path("publicReply")
                        .asText())) {

            throw new IllegalStateException(
                    "公众看到的结案结果不正确："
                            + completedFeedback);
        }

// 管理员详情也应是COMPLETED。
        JsonNode completedAdminFeedback = get(
                admin,
                adminFeedbackPath + "/" + feedbackId,
                200);

        if (!"COMPLETED".equals(
                completedAdminFeedback
                        .path("status")
                        .asText())
                || !"COMPLETED".equals(
                completedAdminFeedback
                        .path("taskStatus")
                        .asText())
                || !completedReply.equals(
                completedAdminFeedback
                        .path("publicReply")
                        .asText())) {

            throw new IllegalStateException(
                    "管理员看到的结案结果不正确："
                            + completedAdminFeedback);
        }

        // 五、瞬时检测直接普通结案。
        String instantReviewPath =
                adminMeasurementPath
                        + "/"
                        + instantMeasurementId
                        + "/review";

        String instantReply =
                "工作人员已到现场核查，瞬时检测仅作现场参考，未发现持续污染现象。";

        JsonNode instantCompletedReview = postJson(
                admin,
                instantReviewPath,
                Map.of(
                        "decision", "COMPLETE",
                        "opinion",
                        "现场核查结果完整，同意普通结案",
                        "publicReply",
                        instantReply),
                200);

        checkReviewResponse(
                instantCompletedReview,
                instantMeasurementId,
                "COMPLETE",
                "APPROVED",
                "COMPLETED",
                "COMPLETED");

        JsonNode instantCompletedFeedback = get(
                secondCitizen,
                "/api/feedbacks/" + instantFeedbackId,
                200);

        if (!"COMPLETED".equals(
                instantCompletedFeedback
                        .path("status")
                        .asText())
                || !instantReply.equals(
                instantCompletedFeedback
                        .path("publicReply")
                        .asText())) {

            throw new IllegalStateException(
                    "瞬时检测结案后的公众反馈不正确："
                            + instantCompletedFeedback);
        }

        // 六、复核状态筛选。
        JsonNode returnedList = get(
                admin,
                adminMeasurementPath
                        + "?reviewStatus=RETURNED",
                200);

        requireRecordId(
                returnedList,
                measurementId,
                true);

        requireRecordId(
                returnedList,
                secondMeasurementId,
                false);

        JsonNode approvedList = get(
                admin,
                adminMeasurementPath
                        + "?reviewStatus=APPROVED",
                200);

        requireRecordId(
                approvedList,
                secondMeasurementId,
                true);

        requireRecordId(
                approvedList,
                instantMeasurementId,
                true);

        JsonNode finalPendingList = get(
                admin,
                adminMeasurementPath
                        + "?reviewStatus=PENDING",
                200);

        requireRecordId(
                finalPendingList,
                measurementId,
                false);

        requireRecordId(
                finalPendingList,
                secondMeasurementId,
                false);

        requireRecordId(
                finalPendingList,
                instantMeasurementId,
                false);

        System.out.println(
                "检测复核状态筛选检查通过");

        System.out.println(
                "复核、退回、补测和普通反馈结案全部通过");

        // =====================================================
        // 规则检测与异常事件持久化检查
        // =====================================================

        checkStoredAnomalyEvent(
                measurementId,
                false,
                null,
                null);

        checkStoredAnomalyEvent(
                instantMeasurementId,
                false,
                null,
                null);

        // insideGridId 已有两条本轮反馈；再创建一条后，
        // 应当触发“同网格近期集中反馈”规则。
        AssignedCase concentratedCase =
                createAssignedCase(
                        secondCitizen,
                        admin,
                        insideGridId,
                        secondId,
                        "集中反馈规则检查");

        String concentratedMeasurementPath =
                "/api/tasks/"
                        + concentratedCase.taskId()
                        + "/measurements";

        Map<String, Object> concentratedRequest =
                measurementRequest(
                        "DAILY",
                        true,
                        null);

        JsonNode concentratedMeasurement = postJson(
                secondClient,
                concentratedMeasurementPath,
                concentratedRequest,
                201);

        long concentratedMeasurementId =
                concentratedMeasurement.path("id").asLong();

        checkRuleResult(
                concentratedMeasurement,
                "SUSPECTED",
                "MEDIUM",
                "集中反馈");

        // 重复提交被任务状态阻止，异常事件也不能重复生成。
        postJson(
                secondClient,
                concentratedMeasurementPath,
                concentratedRequest,
                409);

        checkStoredAnomalyEvent(
                concentratedMeasurementId,
                true,
                "MEDIUM",
                "集中反馈");

        System.out.println(
                "同网格集中反馈与异常事件去重检查通过");

        // childGridId 先提交并通过一条 AQI=100 的基线记录。
        AssignedCase baselineCase =
                createAssignedCase(
                        secondCitizen,
                        admin,
                        childGridId,
                        secondId,
                        "AQI突变基线检查");

        JsonNode baselineMeasurement = postJson(
                secondClient,
                "/api/tasks/"
                        + baselineCase.taskId()
                        + "/measurements",
                measurementRequest(
                        "DAILY",
                        true,
                        null),
                201);

        long baselineMeasurementId =
                baselineMeasurement.path("id").asLong();

        postJson(
                admin,
                adminMeasurementPath
                        + "/"
                        + baselineMeasurementId
                        + "/review",
                Map.of(
                        "decision", "COMPLETE",
                        "opinion", "作为同网格同口径比较基线",
                        "publicReply", "基线检测已完成"),
                200);

        // 新记录 AQI=20，与已通过的基线 AQI=100 相差80。
        AssignedCase jumpCase =
                createAssignedCase(
                        secondCitizen,
                        admin,
                        childGridId,
                        secondId,
                        "AQI短时突变检查");

        Map<String, Object> lowAqiRequest =
                measurementRequest(
                        "DAILY",
                        true,
                        null);

        lowAqiRequest.put("so2", 20);
        lowAqiRequest.put("no2", 16);
        lowAqiRequest.put("co", 0.8);
        lowAqiRequest.put("o3", 40);
        lowAqiRequest.put("pm10", 20);
        lowAqiRequest.put("pm25", 14);
        lowAqiRequest.put(
                "siteNote",
                "用于验证同网格同口径AQI突变规则");

        JsonNode jumpMeasurement = postJson(
                secondClient,
                "/api/tasks/"
                        + jumpCase.taskId()
                        + "/measurements",
                lowAqiRequest,
                201);

        long jumpMeasurementId =
                jumpMeasurement.path("id").asLong();

        if (jumpMeasurement.path("aqi").asInt() != 20) {
            throw new IllegalStateException(
                    "突变样例AQI应为20："
                            + jumpMeasurement);
        }

        checkRuleResult(
                jumpMeasurement,
                "SUSPECTED",
                "MEDIUM",
                "AQI变化80");

        checkStoredAnomalyEvent(
                jumpMeasurementId,
                true,
                "MEDIUM",
                "AQI变化80");

        System.out.println(
                "同网格同口径AQI突变检查通过");

        // PM2.5=151 对应 AQI 大于200，应建议HIGH。
        AssignedCase highAqiCase =
                createAssignedCase(
                        secondCitizen,
                        admin,
                        childGridId,
                        secondId,
                        "AQI超限检查");

        Map<String, Object> highAqiRequest =
                measurementRequest(
                        "DAILY",
                        true,
                        null);

        highAqiRequest.put("pm25", 151);
        highAqiRequest.put(
                "siteNote",
                "用于验证AQI超限和最高优先级合并规则");

        JsonNode highAqiMeasurement = postJson(
                secondClient,
                "/api/tasks/"
                        + highAqiCase.taskId()
                        + "/measurements",
                highAqiRequest,
                201);

        long highAqiMeasurementId =
                highAqiMeasurement.path("id").asLong();

        if (highAqiMeasurement.path("aqi").asInt() <= 200) {
            throw new IllegalStateException(
                    "高AQI样例应大于200："
                            + highAqiMeasurement);
        }

        checkRuleResult(
                highAqiMeasurement,
                "SUSPECTED",
                "HIGH",
                "超过项目触发值100");

        checkStoredAnomalyEvent(
                highAqiMeasurementId,
                true,
                "HIGH",
                "超过项目触发值100");

        System.out.println(
                "AQI超限、多规则合并与建议优先级检查通过");

        // =====================================================
        // 管理员异常事件查询与复核检查
        // =====================================================

        long concentratedEventId =
                findAnomalyEventId(concentratedMeasurementId);
        long jumpEventId =
                findAnomalyEventId(jumpMeasurementId);
        long highAqiEventId =
                findAnomalyEventId(highAqiMeasurementId);

        String adminAnomalyPath =
                "/api/admin/anomalies";

        // 一、列表接口的身份、角色和筛选检查。
        get(newClient(), adminAnomalyPath, 401);
        get(citizen, adminAnomalyPath, 403);
        get(secondClient, adminAnomalyPath, 403);
        get(decisionAfterRemoval, adminAnomalyPath, 403);

        JsonNode pendingAnomalies = get(
                admin,
                adminAnomalyPath + "?status=PENDING_REVIEW",
                200);

        requireRecordId(pendingAnomalies, concentratedEventId, true);
        requireRecordId(pendingAnomalies, jumpEventId, true);
        requireRecordId(pendingAnomalies, highAqiEventId, true);

        JsonNode mediumAnomalies = get(
                admin,
                adminAnomalyPath + "?priority=MEDIUM",
                200);

        requireRecordId(mediumAnomalies, concentratedEventId, true);
        requireRecordId(mediumAnomalies, jumpEventId, true);

        JsonNode highAnomalies = get(
                admin,
                adminAnomalyPath + "?priority=HIGH",
                200);

        requireRecordId(highAnomalies, highAqiEventId, true);

        get(admin, adminAnomalyPath + "?status=UNKNOWN", 400);
        get(admin, adminAnomalyPath + "?priority=URGENT", 400);

        // 二、详情接口和事件复核请求校验。
        String concentratedEventPath =
                adminAnomalyPath + "/" + concentratedEventId;
        String concentratedReviewPath =
                concentratedEventPath + "/review";

        get(newClient(), concentratedEventPath, 401);
        get(citizen, concentratedEventPath, 403);
        get(secondClient, concentratedEventPath, 403);
        get(decisionAfterRemoval, concentratedEventPath, 403);
        get(admin, adminAnomalyPath + "/0", 400);
        get(admin, adminAnomalyPath + "/" + Long.MAX_VALUE, 404);

        checkAnomalyResponse(
                get(admin, concentratedEventPath, 200),
                concentratedEventId,
                concentratedMeasurementId,
                "PENDING_REVIEW",
                "MEDIUM",
                null);

        Map<String, Object> excludeRequest = Map.of(
                "decision", "EXCLUDE",
                "reviewReason", "现场复核后确认不是污染异常");

        postJson(newClient(), concentratedReviewPath, excludeRequest, 401);
        postJson(citizen, concentratedReviewPath, excludeRequest, 403);
        postJson(secondClient, concentratedReviewPath, excludeRequest, 403);
        postJson(decisionAfterRemoval, concentratedReviewPath, excludeRequest, 403);
        postJson(
                admin,
                adminAnomalyPath + "/0/review",
                excludeRequest,
                400);
        postJson(
                admin,
                adminAnomalyPath + "/" + Long.MAX_VALUE + "/review",
                excludeRequest,
                404);
        postJson(
                admin,
                concentratedReviewPath,
                Map.of(
                        "decision", "UNKNOWN",
                        "reviewReason", "错误决定值"),
                400);
        postJson(
                admin,
                concentratedReviewPath,
                Map.of(
                        "decision", "EXCLUDE",
                        "reviewReason", ""),
                400);
        postJson(
                admin,
                concentratedReviewPath,
                Map.of(
                        "decision", "CONFIRM",
                        "reviewReason", "缺少最终优先级和公众说明"),
                400);

        // 有活动异常事件时，普通检测结案必须被拦截。
        postJson(
                admin,
                adminMeasurementPath
                        + "/"
                        + highAqiMeasurementId
                        + "/review",
                Map.of(
                        "decision", "COMPLETE",
                        "opinion", "尝试绕过异常复核",
                        "publicReply", "不应保存"),
                409);

        // 三、排除集中反馈异常后，可以继续走普通结案。
        checkAnomalyResponse(
                postJson(
                        admin,
                        concentratedReviewPath,
                        excludeRequest,
                        200),
                concentratedEventId,
                concentratedMeasurementId,
                "EXCLUDED",
                "MEDIUM",
                null);

        postJson(admin, concentratedReviewPath, excludeRequest, 409);

        JsonNode concentratedCompleted = postJson(
                admin,
                adminMeasurementPath
                        + "/"
                        + concentratedMeasurementId
                        + "/review",
                Map.of(
                        "decision", "COMPLETE",
                        "opinion", "已排除异常，可以普通结案",
                        "publicReply", "该反馈已经核查并完成办理"),
                200);

        checkReviewResponse(
                concentratedCompleted,
                concentratedMeasurementId,
                "COMPLETE",
                "APPROVED",
                "COMPLETED",
                "COMPLETED");

        // 四、确认高AQI异常，保留建议优先级并记录最终优先级。
        String highReviewPath =
                adminAnomalyPath
                        + "/"
                        + highAqiEventId
                        + "/review";

        checkAnomalyResponse(
                postJson(
                        admin,
                        highReviewPath,
                        Map.of(
                                "decision", "CONFIRM",
                                "confirmedPriority", "MEDIUM",
                                "reviewReason", "检测数据有效，确认存在污染异常",
                                "publicReply", "已确认异常并转入后续处置"),
                        200),
                highAqiEventId,
                highAqiMeasurementId,
                "PROCESSING",
                "HIGH",
                "MEDIUM");

        postJson(
                admin,
                highReviewPath,
                Map.of(
                        "decision", "CONFIRM",
                        "confirmedPriority", "HIGH",
                        "reviewReason", "重复确认",
                        "publicReply", "不应保存"),
                409);

        JsonNode confirmedMeasurement = get(
                admin,
                adminMeasurementPath + "/" + highAqiMeasurementId,
                200);

        if (!"APPROVED".equals(
                confirmedMeasurement.path("reviewStatus").asText())) {
            throw new IllegalStateException(
                    "确认异常后检测记录应为APPROVED："
                            + confirmedMeasurement);
        }

        JsonNode confirmedFeedback = get(
                admin,
                "/api/admin/feedbacks/" + highAqiCase.feedbackId(),
                200);

        if (!"PROCESSING".equals(
                confirmedFeedback.path("status").asText())
                || !"已确认异常并转入后续处置".equals(
                confirmedFeedback.path("publicReply").asText())) {
            throw new IllegalStateException(
                    "确认异常后反馈状态或公众说明不正确："
                            + confirmedFeedback);
        }

        // =====================================================
        // 管理员内部预警查询检查
        // =====================================================

        long warningId =
                findWarningId(highAqiEventId);

        String adminWarningPath =
                "/api/admin/warnings";

        String warningDetailPath =
                adminWarningPath + "/" + warningId;

        get(newClient(), adminWarningPath, 401);
        get(citizen, adminWarningPath, 403);
        get(secondClient, adminWarningPath, 403);
        get(decisionAfterRemoval, adminWarningPath, 403);

        JsonNode activeWarnings = get(
                admin,
                adminWarningPath + "?status=ACTIVE",
                200);

        requireRecordId(
                activeWarnings,
                warningId,
                true);

        JsonNode mediumWarnings = get(
                admin,
                adminWarningPath + "?level=MEDIUM",
                200);

        requireRecordId(
                mediumWarnings,
                warningId,
                true);

        get(
                admin,
                adminWarningPath + "?status=UNKNOWN",
                400);

        get(
                admin,
                adminWarningPath + "?level=URGENT",
                400);

        get(newClient(), warningDetailPath, 401);
        get(citizen, warningDetailPath, 403);
        get(secondClient, warningDetailPath, 403);
        get(decisionAfterRemoval, warningDetailPath, 403);

        checkWarningResponse(
                get(admin, warningDetailPath, 200),
                warningId,
                highAqiEventId,
                childGridId,
                "ACTIVE",
                false);

        get(admin, adminWarningPath + "/0", 400);
        get(
                admin,
                adminWarningPath + "/" + Long.MAX_VALUE,
                404);

        System.out.println(
                "内部预警查询、筛选和角色权限检查通过");

        // =====================================================
        // 管理员处置工单查询与指派检查
        // =====================================================

        long workOrderId =
                findWorkOrderId(highAqiEventId);

        String adminWorkOrderPath =
                "/api/admin/work-orders";

        String workOrderDetailPath =
                adminWorkOrderPath + "/" + workOrderId;

        String workOrderAssignPath =
                workOrderDetailPath + "/assign";

        // 一、列表接口的身份、角色和筛选检查。
        get(newClient(), adminWorkOrderPath, 401);
        get(citizen, adminWorkOrderPath, 403);
        get(secondClient, adminWorkOrderPath, 403);
        get(decisionAfterRemoval, adminWorkOrderPath, 403);

        JsonNode pendingConfirmOrders = get(
                admin,
                adminWorkOrderPath
                        + "?status=PENDING_CONFIRM",
                200);

        requireRecordId(
                pendingConfirmOrders,
                workOrderId,
                true);

        JsonNode mediumOrders = get(
                admin,
                adminWorkOrderPath + "?priority=MEDIUM",
                200);

        requireRecordId(
                mediumOrders,
                workOrderId,
                true);

        get(
                admin,
                adminWorkOrderPath + "?status=UNKNOWN",
                400);

        get(
                admin,
                adminWorkOrderPath + "?priority=URGENT",
                400);

        // 二、详情接口权限与ID校验。
        get(newClient(), workOrderDetailPath, 401);
        get(citizen, workOrderDetailPath, 403);
        get(secondClient, workOrderDetailPath, 403);
        get(decisionAfterRemoval, workOrderDetailPath, 403);

        checkWorkOrderResponse(
                get(admin, workOrderDetailPath, 200),
                workOrderId,
                highAqiEventId,
                highAqiCase.feedbackId(),
                "PENDING_CONFIRM",
                null,
                null);

        get(admin, adminWorkOrderPath + "/0", 400);
        get(
                admin,
                adminWorkOrderPath + "/" + Long.MAX_VALUE,
                404);

        Map<String, Object> assignWorkOrderRequest =
                Map.of(
                        "assigneeId", secondId,
                        "requirement", "尽快到现场采取污染控制措施");

        // 三、指派接口权限、ID和请求参数检查。
        postJson(
                newClient(),
                workOrderAssignPath,
                assignWorkOrderRequest,
                401);

        postJson(
                citizen,
                workOrderAssignPath,
                assignWorkOrderRequest,
                403);

        postJson(
                secondClient,
                workOrderAssignPath,
                assignWorkOrderRequest,
                403);

        postJson(
                decisionAfterRemoval,
                workOrderAssignPath,
                assignWorkOrderRequest,
                403);

        postJson(
                admin,
                adminWorkOrderPath + "/0/assign",
                assignWorkOrderRequest,
                400);

        postJson(
                admin,
                adminWorkOrderPath
                        + "/"
                        + Long.MAX_VALUE
                        + "/assign",
                assignWorkOrderRequest,
                404);

        postJson(
                admin,
                workOrderAssignPath,
                Map.of(
                        "assigneeId", 0,
                        "requirement", "错误网格员ID"),
                400);

        postJson(
                admin,
                workOrderAssignPath,
                Map.of(
                        "assigneeId", secondId,
                        "requirement", ""),
                400);

        // 公众不是网格员。
        postJson(
                admin,
                workOrderAssignPath,
                Map.of(
                        "assigneeId", publicUser.path("id").asLong(),
                        "requirement", "错误角色检查"),
                400);

        // 第一名网格员的网格关联已被撤销。
        postJson(
                admin,
                workOrderAssignPath,
                Map.of(
                        "assigneeId", workerId,
                        "requirement", "错误网格范围检查"),
                400);

        // 已停用的网格员不能接收工单。
        String secondEnabledPath =
                "/api/admin/users/"
                        + secondId
                        + "/enabled";

        postJson(
                admin,
                secondEnabledPath,
                Map.of("enabled", false),
                200);

        try {
            postJson(
                    admin,
                    workOrderAssignPath,
                    assignWorkOrderRequest,
                    400);
        } finally {
            postJson(
                    admin,
                    secondEnabledPath,
                    Map.of("enabled", true),
                    200);
        }

        // 四、正常指派及重复请求检查。
        checkWorkOrderResponse(
                postJson(
                        admin,
                        workOrderAssignPath,
                        assignWorkOrderRequest,
                        200),
                workOrderId,
                highAqiEventId,
                highAqiCase.feedbackId(),
                "PENDING",
                secondId,
                "尽快到现场采取污染控制措施");

        // 完全相同的重复请求应当幂等返回。
        postJson(
                admin,
                workOrderAssignPath,
                assignWorkOrderRequest,
                200);

        // 已指派工单不能通过首次指派接口修改要求。
        postJson(
                admin,
                workOrderAssignPath,
                Map.of(
                        "assigneeId", secondId,
                        "requirement", "尝试修改已经确认的工单"),
                409);

        JsonNode assignedOrder = get(
                admin,
                workOrderDetailPath,
                200);

        checkWorkOrderResponse(
                assignedOrder,
                workOrderId,
                highAqiEventId,
                highAqiCase.feedbackId(),
                "PENDING",
                secondId,
                "尽快到现场采取污染控制措施");

        JsonNode pendingOrders = get(
                admin,
                adminWorkOrderPath + "?status=PENDING",
                200);

        requireRecordId(
                pendingOrders,
                workOrderId,
                true);

        System.out.println(
                "处置工单查询、权限、指派和重复请求检查全部通过");

        // =====================================================
        // 网格员本人处置工单与结果提交检查
        // =====================================================

        HttpClient workOrderWorker = newClient();
        login(
                workOrderWorker,
                secondPhone,
                DEMO_PASSWORD);

        // =====================================================
        // 网格员统一任务列表检查
        // =====================================================

        String unifiedTaskPath =
                "/api/grid/tasks";

        get(newClient(), unifiedTaskPath, 401);
        get(citizen, unifiedTaskPath, 403);
        get(admin, unifiedTaskPath, 403);
        get(decisionAfterRemoval, unifiedTaskPath, 403);

        JsonNode unifiedTasks = get(
                workOrderWorker,
                unifiedTaskPath,
                200);

        requireUnifiedTask(
                unifiedTasks,
                "INSPECTION",
                highAqiCase.taskId(),
                "COMPLETED",
                true);

        requireUnifiedTask(
                unifiedTasks,
                "DISPOSAL",
                workOrderId,
                "PENDING",
                true);

        // 第一名网格员不能看到第二名网格员的处置工单。
        requireUnifiedTask(
                get(firstAgain, unifiedTaskPath, 200),
                "DISPOSAL",
                workOrderId,
                "PENDING",
                false);

        JsonNode inspectionOnlyTasks = get(
                workOrderWorker,
                unifiedTaskPath
                        + "?taskType=INSPECTION",
                200);

        requireUnifiedTask(
                inspectionOnlyTasks,
                "INSPECTION",
                highAqiCase.taskId(),
                "COMPLETED",
                true);

        requireUnifiedTask(
                inspectionOnlyTasks,
                "DISPOSAL",
                workOrderId,
                "PENDING",
                false);

        JsonNode pendingDisposalTasks = get(
                workOrderWorker,
                unifiedTaskPath
                        + "?taskType=DISPOSAL&status=PENDING",
                200);

        requireUnifiedTask(
                pendingDisposalTasks,
                "DISPOSAL",
                workOrderId,
                "PENDING",
                true);

        get(
                workOrderWorker,
                unifiedTaskPath + "?taskType=UNKNOWN",
                400);

        get(
                workOrderWorker,
                unifiedTaskPath + "?status=UNKNOWN",
                400);

        System.out.println(
                "统一任务列表、类型筛选和数据隔离检查通过");

        String workerWorkOrderPath =
                "/api/grid/work-orders";

        String workerOrderDetailPath =
                workerWorkOrderPath + "/" + workOrderId;

        String submitWorkOrderResultPath =
                workerOrderDetailPath + "/result";

        // 一、本人列表的数据隔离。
        get(newClient(), workerWorkOrderPath, 401);
        get(citizen, workerWorkOrderPath, 403);
        get(admin, workerWorkOrderPath, 403);
        get(decisionAfterRemoval, workerWorkOrderPath, 403);

        JsonNode otherWorkerOrders = get(
                firstAgain,
                workerWorkOrderPath,
                200);

        requireRecordId(
                otherWorkerOrders,
                workOrderId,
                false);

        JsonNode myWorkOrders = get(
                workOrderWorker,
                workerWorkOrderPath,
                200);

        requireRecordId(
                myWorkOrders,
                workOrderId,
                true);

        // 二、本人详情权限和ID校验。
        get(newClient(), workerOrderDetailPath, 401);
        get(citizen, workerOrderDetailPath, 403);
        get(admin, workerOrderDetailPath, 403);
        get(decisionAfterRemoval, workerOrderDetailPath, 403);
        get(firstAgain, workerOrderDetailPath, 404);

        JsonNode workerOrder = get(
                workOrderWorker,
                workerOrderDetailPath,
                200);

        checkWorkOrderResponse(
                workerOrder,
                workOrderId,
                highAqiEventId,
                highAqiCase.feedbackId(),
                "PENDING",
                secondId,
                "尽快到现场采取污染控制措施");

        // 三、工单图片权限、上传、列表和下载检查。
        String workOrderAttachmentPath =
                workerOrderDetailPath + "/attachments";

        postMultipart(
                newClient(),
                workOrderAttachmentPath,
                "disposal.png",
                "image/png",
                testPng,
                401);

        postMultipart(
                citizen,
                workOrderAttachmentPath,
                "disposal.png",
                "image/png",
                testPng,
                403);

        postMultipart(
                admin,
                workOrderAttachmentPath,
                "disposal.png",
                "image/png",
                testPng,
                403);

        postMultipart(
                firstAgain,
                workOrderAttachmentPath,
                "disposal.png",
                "image/png",
                testPng,
                404);

        get(newClient(), workOrderAttachmentPath, 401);
        get(citizen, workOrderAttachmentPath, 403);
        get(decisionAfterRemoval, workOrderAttachmentPath, 403);
        get(firstAgain, workOrderAttachmentPath, 404);

        if (get(workOrderWorker, workOrderAttachmentPath, 200)
                .size() != 0
                || get(admin, workOrderAttachmentPath, 200)
                .size() != 0) {

            throw new IllegalStateException(
                    "上传前工单附件列表应为空");
        }

        JsonNode workOrderAttachment = postMultipart(
                workOrderWorker,
                workOrderAttachmentPath,
                "disposal.png",
                "image/png",
                testPng,
                201);

        long workOrderAttachmentId =
                workOrderAttachment.path("id").asLong();

        if (workOrderAttachmentId <= 0
                || !"image/png".equals(
                workOrderAttachment.path("contentType").asText())) {

            throw new IllegalStateException(
                    "工单附件上传响应不正确："
                            + workOrderAttachment);
        }

        requireRecordId(
                get(
                        workOrderWorker,
                        workOrderAttachmentPath,
                        200),
                workOrderAttachmentId,
                true);

        requireRecordId(
                get(admin, workOrderAttachmentPath, 200),
                workOrderAttachmentId,
                true);

        String workOrderAttachmentContentPath =
                "/api/attachments/"
                        + workOrderAttachmentId
                        + "/content";

        if (!Arrays.equals(
                testPng,
                getBytes(
                        workOrderWorker,
                        workOrderAttachmentContentPath,
                        200))
                || !Arrays.equals(
                testPng,
                getBytes(
                        admin,
                        workOrderAttachmentContentPath,
                        200))) {

            throw new IllegalStateException(
                    "下载的工单图片与上传内容不一致");
        }

        getBytes(
                firstAgain,
                workOrderAttachmentContentPath,
                404);

        getBytes(
                citizen,
                workOrderAttachmentContentPath,
                403);

        getBytes(
                decisionAfterRemoval,
                workOrderAttachmentContentPath,
                403);

        get(workOrderWorker, workerWorkOrderPath + "/0", 400);
        get(
                workOrderWorker,
                workerWorkOrderPath + "/" + Long.MAX_VALUE,
                404);

        LocalDateTime assignedAt = LocalDateTime.parse(
                workerOrder.path("assignedAt").asText());

        Map<String, Object> validWorkOrderResult =
                Map.of(
                        "handledAt",
                        assignedAt.toString(),
                        "measures",
                        "对现场污染源进行临时停运，并清理散落污染物。",
                        "result",
                        "现场异味明显减弱，污染扩散已经得到控制。");

        // 三、结果提交接口权限。
        postJson(
                newClient(),
                submitWorkOrderResultPath,
                validWorkOrderResult,
                401);

        postJson(
                citizen,
                submitWorkOrderResultPath,
                validWorkOrderResult,
                403);

        postJson(
                admin,
                submitWorkOrderResultPath,
                validWorkOrderResult,
                403);

        postJson(
                decisionAfterRemoval,
                submitWorkOrderResultPath,
                validWorkOrderResult,
                403);

        // 其他网格员不能提交该工单。
        postJson(
                firstAgain,
                submitWorkOrderResultPath,
                validWorkOrderResult,
                404);

        postJson(
                workOrderWorker,
                workerWorkOrderPath + "/0/result",
                validWorkOrderResult,
                400);

        postJson(
                workOrderWorker,
                workerWorkOrderPath
                        + "/"
                        + Long.MAX_VALUE
                        + "/result",
                validWorkOrderResult,
                404);

        // 四、处理时间和必填内容校验。
        postJson(
                workOrderWorker,
                submitWorkOrderResultPath,
                Map.of(
                        "handledAt",
                        assignedAt.minusMinutes(1).toString(),
                        "measures", "时间边界检查",
                        "result", "不应保存"),
                400);

        postJson(
                workOrderWorker,
                submitWorkOrderResultPath,
                Map.of(
                        "handledAt",
                        LocalDateTime.now()
                                .plusDays(1)
                                .toString(),
                        "measures", "未来时间检查",
                        "result", "不应保存"),
                400);

        postJson(
                workOrderWorker,
                submitWorkOrderResultPath,
                Map.of(
                        "handledAt", LocalDateTime.now().toString(),
                        "measures", "",
                        "result", "不应保存"),
                400);

        postJson(
                workOrderWorker,
                submitWorkOrderResultPath,
                Map.of(
                        "handledAt", LocalDateTime.now().toString(),
                        "measures", "处置措施检查",
                        "result", ""),
                400);

        // 五、正常提交后进入待复核。
        JsonNode submittedOrder = postJson(
                workOrderWorker,
                submitWorkOrderResultPath,
                validWorkOrderResult,
                200);

        checkWorkOrderResponse(
                submittedOrder,
                workOrderId,
                highAqiEventId,
                highAqiCase.feedbackId(),
                "PENDING_REVIEW",
                secondId,
                "尽快到现场采取污染控制措施");

        if (!"对现场污染源进行临时停运，并清理散落污染物。"
                .equals(submittedOrder.path("measures").asText())
                || !"现场异味明显减弱，污染扩散已经得到控制。"
                .equals(submittedOrder.path("result").asText())
                || !submittedOrder.path("handledAt").isTextual()
                || !submittedOrder.path("submittedAt").isTextual()) {

            throw new IllegalStateException(
                    "处置结果或提交时间不正确："
                            + submittedOrder);
        }

        // 提交处置结果后不能继续补传图片。
        postMultipart(
                workOrderWorker,
                workOrderAttachmentPath,
                "late.png",
                "image/png",
                testPng,
                409);

        // 同一张待复核工单不能重复提交。
        postJson(
                workOrderWorker,
                submitWorkOrderResultPath,
                validWorkOrderResult,
                409);

        JsonNode pendingReviewOrders = get(
                admin,
                adminWorkOrderPath
                        + "?status=PENDING_REVIEW",
                200);

        requireRecordId(
                pendingReviewOrders,
                workOrderId,
                true);

        requireUnifiedTask(
                get(
                        workOrderWorker,
                        unifiedTaskPath
                                + "?taskType=DISPOSAL"
                                + "&status=PENDING_REVIEW",
                        200),
                "DISPOSAL",
                workOrderId,
                "PENDING_REVIEW",
                true);

        JsonNode noLongerPendingOrders = get(
                admin,
                adminWorkOrderPath + "?status=PENDING",
                200);

        requireRecordId(
                noLongerPendingOrders,
                workOrderId,
                false);

        System.out.println(
                "网格员工单查询、数据隔离和处置结果提交全部通过");

        // 五、退回异常检测时，应当自动排除对应待复核事件。
        postJson(
                admin,
                adminMeasurementPath
                        + "/"
                        + jumpMeasurementId
                        + "/review",
                Map.of(
                        "decision", "RETURN",
                        "opinion", "AQI突变需要重新采样确认"),
                200);

        JsonNode returnedJumpEvent = get(
                admin,
                adminAnomalyPath + "/" + jumpEventId,
                200);

        checkAnomalyResponse(
                returnedJumpEvent,
                jumpEventId,
                jumpMeasurementId,
                "EXCLUDED",
                "MEDIUM",
                null);

        if (!returnedJumpEvent.path("reviewReason")
                .asText()
                .contains("检测记录被退回补充")) {
            throw new IllegalStateException(
                    "退回检测时没有记录自动排除原因："
                            + returnedJumpEvent);
        }

        JsonNode processingAnomalies = get(
                admin,
                adminAnomalyPath + "?status=PROCESSING&priority=MEDIUM",
                200);
        requireRecordId(processingAnomalies, highAqiEventId, true);

        JsonNode excludedAnomalies = get(
                admin,
                adminAnomalyPath + "?status=EXCLUDED",
                200);
        requireRecordId(excludedAnomalies, concentratedEventId, true);
        requireRecordId(excludedAnomalies, jumpEventId, true);

        // =====================================================
        // 管理员工单退回、补充提交与关闭检查
        // =====================================================

        String workOrderReviewPath =
                workOrderDetailPath + "/review";

        Map<String, Object> returnWorkOrderRequest =
                Map.of(
                        "decision", "RETURN",
                        "opinion", "请补充现场清理后的复测说明");

        // 一、复核接口的身份和角色权限。
        postJson(
                newClient(),
                workOrderReviewPath,
                returnWorkOrderRequest,
                401);

        postJson(
                citizen,
                workOrderReviewPath,
                returnWorkOrderRequest,
                403);

        postJson(
                workOrderWorker,
                workOrderReviewPath,
                returnWorkOrderRequest,
                403);

        postJson(
                decisionAfterRemoval,
                workOrderReviewPath,
                returnWorkOrderRequest,
                403);

        // 二、ID和请求参数校验。
        postJson(
                admin,
                adminWorkOrderPath + "/0/review",
                returnWorkOrderRequest,
                400);

        postJson(
                admin,
                adminWorkOrderPath
                        + "/"
                        + Long.MAX_VALUE
                        + "/review",
                returnWorkOrderRequest,
                404);

        postJson(
                admin,
                workOrderReviewPath,
                Map.of(
                        "decision", "APPROVE",
                        "opinion", "错误决定值"),
                400);

        postJson(
                admin,
                workOrderReviewPath,
                Map.of(
                        "decision", "RETURN",
                        "opinion", ""),
                400);

        postJson(
                admin,
                workOrderReviewPath,
                Map.of(
                        "decision", "CLOSE",
                        "opinion", "处置结果符合要求"),
                400);

        // 三、第一次复核退回到PENDING。
        JsonNode returnedWorkOrder = postJson(
                admin,
                workOrderReviewPath,
                returnWorkOrderRequest,
                200);

        checkWorkOrderResponse(
                returnedWorkOrder,
                workOrderId,
                highAqiEventId,
                highAqiCase.feedbackId(),
                "PENDING",
                secondId,
                "尽快到现场采取污染控制措施");

        if (!"请补充现场清理后的复测说明".equals(
                returnedWorkOrder.path("reviewOpinion").asText())
                || returnedWorkOrder.path("reviewedBy").asLong() <= 0
                || !returnedWorkOrder.path("reviewedAt").isTextual()) {

            throw new IllegalStateException(
                    "工单退回复核信息不正确："
                            + returnedWorkOrder);
        }

        // 工单已不是待复核状态，不能连续复核。
        postJson(
                admin,
                workOrderReviewPath,
                returnWorkOrderRequest,
                409);

        // 四、网格员补充结果并再次提交。
        Map<String, Object> supplementedWorkOrderResult =
                Map.of(
                        "handledAt",
                        assignedAt.toString(),
                        "measures",
                        "完成现场清理，并对污染点进行覆盖和隔离。",
                        "result",
                        "复测时现场异味消失，污染扩散已经停止。");

        JsonNode resubmittedWorkOrder = postJson(
                workOrderWorker,
                submitWorkOrderResultPath,
                supplementedWorkOrderResult,
                200);

        checkWorkOrderResponse(
                resubmittedWorkOrder,
                workOrderId,
                highAqiEventId,
                highAqiCase.feedbackId(),
                "PENDING_REVIEW",
                secondId,
                "尽快到现场采取污染控制措施");

        if (!"完成现场清理，并对污染点进行覆盖和隔离。"
                .equals(resubmittedWorkOrder.path("measures").asText())
                || !"复测时现场异味消失，污染扩散已经停止。"
                .equals(resubmittedWorkOrder.path("result").asText())) {

            throw new IllegalStateException(
                    "工单补充提交内容不正确："
                            + resubmittedWorkOrder);
        }

        // 五、第二次复核通过并关闭整个处置流程。
        String finalPublicReply =
                "污染问题已完成现场处置和复核，相关预警已经解除。";

        Map<String, Object> closeWorkOrderRequest =
                Map.of(
                        "decision", "CLOSE",
                        "opinion", "补充材料完整，处置结果符合要求",
                        "publicReply", finalPublicReply);

        JsonNode closedWorkOrder = postJson(
                admin,
                workOrderReviewPath,
                closeWorkOrderRequest,
                200);

        checkWorkOrderResponse(
                closedWorkOrder,
                workOrderId,
                highAqiEventId,
                highAqiCase.feedbackId(),
                "CLOSED",
                secondId,
                "尽快到现场采取污染控制措施");

        if (!"补充材料完整，处置结果符合要求".equals(
                closedWorkOrder.path("reviewOpinion").asText())) {
            throw new IllegalStateException(
                    "工单关闭复核意见不正确："
                            + closedWorkOrder);
        }

        postJson(
                admin,
                workOrderReviewPath,
                closeWorkOrderRequest,
                409);

        postJson(
                workOrderWorker,
                submitWorkOrderResultPath,
                supplementedWorkOrderResult,
                409);

        // 六、检查工单、事件、预警和公众反馈的最终状态。
        JsonNode closedOrders = get(
                admin,
                adminWorkOrderPath + "?status=CLOSED",
                200);

        requireRecordId(
                closedOrders,
                workOrderId,
                true);

        requireUnifiedTask(
                get(
                        workOrderWorker,
                        unifiedTaskPath
                                + "?taskType=DISPOSAL"
                                + "&status=CLOSED",
                        200),
                "DISPOSAL",
                workOrderId,
                "CLOSED",
                true);

        checkAnomalyResponse(
                get(
                        admin,
                        adminAnomalyPath + "/" + highAqiEventId,
                        200),
                highAqiEventId,
                highAqiMeasurementId,
                "CLOSED",
                "HIGH",
                "MEDIUM");

        JsonNode disposedFeedback = get(
                admin,
                "/api/admin/feedbacks/"
                        + highAqiCase.feedbackId(),
                200);

        if (!"COMPLETED".equals(
                disposedFeedback.path("status").asText())
                || !finalPublicReply.equals(
                disposedFeedback.path("publicReply").asText())) {

            throw new IllegalStateException(
                    "工单关闭后反馈状态或公众说明不正确："
                            + disposedFeedback);
        }

        JsonNode publicCompletedFeedback = get(
                secondCitizen,
                "/api/feedbacks/"
                        + highAqiCase.feedbackId(),
                200);

        if (!"COMPLETED".equals(
                publicCompletedFeedback.path("status").asText())
                || !finalPublicReply.equals(
                publicCompletedFeedback.path("publicReply").asText())) {

            throw new IllegalStateException(
                    "公众没有看到最终处置结果："
                            + publicCompletedFeedback);
        }

        checkClosedWarning(highAqiEventId);
        checkWorkOrderReviewHistory(workOrderId);

        checkWarningResponse(
                get(admin, warningDetailPath, 200),
                warningId,
                highAqiEventId,
                childGridId,
                "CLOSED",
                true);

        JsonNode closedWarnings = get(
                admin,
                adminWarningPath + "?status=CLOSED&level=MEDIUM",
                200);

        requireRecordId(
                closedWarnings,
                warningId,
                true);

        // 工单关闭后，历史图片仍可查看。
        requireRecordId(
                get(admin, workOrderAttachmentPath, 200),
                workOrderAttachmentId,
                true);

        getBytes(
                workOrderWorker,
                workOrderAttachmentContentPath,
                200);

        awaitMockAnalysis("FEEDBACK", feedbackId, 1);
        awaitMockAnalysis("MEASUREMENT", measurementId, 1);

        System.out.println(
                "AI初判自动执行、结构化结果和唯一记录检查通过");

        // =====================================================
        // 管理员AI初判查询、失败提示和重试检查
        // =====================================================

        String adminAiPath = "/api/admin/ai-analyses";
        long feedbackAnalysisId = findAnalysisId(
                "FEEDBACK", feedbackId);

        get(newClient(), adminAiPath, 401);
        get(citizen, adminAiPath, 403);
        get(firstAgain, adminAiPath, 403);
        get(decisionAfterRemoval, adminAiPath, 403);

        JsonNode feedbackAnalyses = get(
                admin,
                adminAiPath
                        + "?targetType=FEEDBACK&status=SUCCEEDED",
                200);

        requireRecordId(
                feedbackAnalyses,
                feedbackAnalysisId,
                true);

        checkAiAnalysisResponse(
                get(
                        admin,
                        adminAiPath + "/" + feedbackAnalysisId,
                        200),
                feedbackAnalysisId,
                "FEEDBACK",
                feedbackId,
                "SUCCEEDED",
                1);

        get(admin, adminAiPath + "?targetType=UNKNOWN", 400);
        get(admin, adminAiPath + "?status=UNKNOWN", 400);
        get(admin, adminAiPath + "/0", 400);
        get(
                admin,
                adminAiPath + "/9223372036854775807",
                404);

        String retryPath = adminAiPath
                + "/" + feedbackAnalysisId + "/retry";

        postJson(newClient(), retryPath, Map.of(), 401);
        postJson(citizen, retryPath, Map.of(), 403);
        postJson(firstAgain, retryPath, Map.of(), 403);
        postJson(decisionAfterRemoval, retryPath, Map.of(), 403);
        postJson(admin, retryPath, Map.of(), 409);

        markAnalysisAsTimedOut(feedbackAnalysisId);

        checkAiAnalysisResponse(
                get(
                        admin,
                        adminAiPath + "/" + feedbackAnalysisId,
                        200),
                feedbackAnalysisId,
                "FEEDBACK",
                feedbackId,
                "FAILED",
                1);

        postJson(admin, retryPath, Map.of(), 204);
        awaitMockAnalysis("FEEDBACK", feedbackId, 2);

        checkAiAnalysisResponse(
                get(
                        admin,
                        adminAiPath + "/" + feedbackAnalysisId,
                        200),
                feedbackAnalysisId,
                "FEEDBACK",
                feedbackId,
                "SUCCEEDED",
                2);

        System.out.println(
                "AI初判查询、失败显示、权限和人工重试检查通过");

        System.out.println(
                "工单退回、补充、关闭、附件和公众结果联动全部通过");

        System.out.println(
                "异常事件查询、确认、排除和状态联动全部通过");

        System.out.println(
                "异常规则接口集成与数据库事件检查全部通过");

        System.out.println(
                "瞬时检测的人工复核和普通结案检查通过");

        System.out.println(
                "补测通过、普通结案和公众办理说明检查通过");

        System.out.println(
                "检测退回、历史保留和第2版重新提交检查通过");

        System.out.println(
                "检测复核的权限、ID和请求参数检查通过");

        System.out.println(
                "管理员检测查询、筛选和角色权限检查通过");

        System.out.println(
                "检测记录、任务与反馈状态流转检查通过");

        System.out.println(
                "本轮正式检测记录ID：" + measurementId);

        System.out.println(
                "本轮瞬时检测记录ID："
                        + instantResult.path("id").asLong());

        System.out.println(
                "SELECT COUNT(*) AS task_count "
                        + "FROM neps.biz_inspection_task "
                        + "WHERE feedback_id = "
                        + feedbackId + ";");

        System.out.println(
                "SELECT action, from_assignee_id, "
                        + "to_assignee_id, from_status, to_status "
                        + "FROM neps.biz_operation_log "
                        + "WHERE business_type = 'FEEDBACK' "
                        + "AND business_id = "
                        + feedbackId
                        + " ORDER BY id;");

        System.out.println(
                "SELECT id, feedback_id, assignee_id, assigned_by, "
                        + "requirement, priority, status, assigned_at "
                        + "FROM neps.biz_inspection_task "
                        + "WHERE feedback_id = " + feedbackId + ";");

        System.out.println(
                "SELECT action, operator_id, from_status, to_status, "
                        + "from_assignee_id, to_assignee_id, remark "
                        + "FROM neps.biz_operation_log "
                        + "WHERE business_type = 'FEEDBACK' "
                        + "AND business_id = " + feedbackId
                        + " ORDER BY id;");

        System.out.println(
                "SELECT f.id, f.submitter_id, f.status, "
                        + "a.target_type, a.status AS analysis_status "
                        + "FROM neps.biz_feedback f "
                        + "JOIN neps.biz_ai_analysis a "
                        + "ON a.target_type = 'FEEDBACK' "
                        + "AND a.target_id = f.id "
                        + "WHERE f.id = " + feedbackId + ";");

// 直接输出本轮数据库核对语句，避免忘记替换手机号。
        System.out.println(
                "SELECT region_id FROM neps.sys_user_region WHERE user_id = "
                        + decisionId + " ORDER BY region_id;");

        System.out.println(
                "SELECT id, task_id, feedback_id, submitter_id, "
                        + "version_no, report_type, quality_flag, "
                        + "aqi_calculable, aqi, aqi_level, "
                        + "aqi_category, primary_pollutants, "
                        + "review_status, standard_version "
                        + "FROM neps.biz_measurement "
                        + "WHERE feedback_id IN ("
                        + feedbackId + ", "
                        + instantFeedbackId + ") "
                        + "ORDER BY id;");

        System.out.println(
                "SELECT id, target_type, target_id, status "
                        + "FROM neps.biz_ai_analysis "
                        + "WHERE target_type = 'MEASUREMENT' "
                        + "AND target_id IN ("
                        + "SELECT id FROM neps.biz_measurement "
                        + "WHERE feedback_id IN ("
                        + feedbackId + ", "
                        + instantFeedbackId + ")) "
                        + "ORDER BY id;");

        System.out.println(
                "SELECT f.id AS feedback_id, f.status AS feedback_status, "
                        + "t.id AS task_id, t.status AS task_status, "
                        + "m.id AS measurement_id, "
                        + "m.review_status, m.aqi "
                        + "FROM neps.biz_feedback f "
                        + "JOIN neps.biz_inspection_task t "
                        + "ON t.feedback_id = f.id "
                        + "JOIN neps.biz_measurement m "
                        + "ON m.task_id = t.id "
                        + "WHERE f.id IN ("
                        + feedbackId + ", "
                        + instantFeedbackId + ") "
                        + "ORDER BY f.id;");

        System.out.println(
                "SELECT id, task_id, feedback_id, "
                        + "version_no, review_status, "
                        + "aqi, quality_flag, submitted_at "
                        + "FROM neps.biz_measurement "
                        + "WHERE feedback_id IN ("
                        + feedbackId + ", "
                        + instantFeedbackId + ") "
                        + "ORDER BY feedback_id, version_no;");

        System.out.println(
                "SELECT r.id, r.measurement_id, "
                        + "r.reviewer_id, r.decision, "
                        + "r.opinion, r.public_reply, r.reviewed_at "
                        + "FROM neps.biz_measurement_review r "
                        + "WHERE r.measurement_id IN ("
                        + measurementId + ", "
                        + secondMeasurementId + ", "
                        + instantMeasurementId + ") "
                        + "ORDER BY r.id;");

        System.out.println(
                "SELECT f.id AS feedback_id, "
                        + "f.status AS feedback_status, "
                        + "f.public_reply, "
                        + "t.status AS task_status "
                        + "FROM neps.biz_feedback f "
                        + "JOIN neps.biz_inspection_task t "
                        + "ON t.feedback_id = f.id "
                        + "WHERE f.id IN ("
                        + feedbackId + ", "
                        + instantFeedbackId + ") "
                        + "ORDER BY f.id;");

        System.out.println(
                "SELECT action, from_status, to_status, remark "
                        + "FROM neps.biz_operation_log "
                        + "WHERE business_type = 'FEEDBACK' "
                        + "AND business_id IN ("
                        + feedbackId + ", "
                        + instantFeedbackId + ") "
                        + "ORDER BY id;");

        System.out.println(
                "SELECT id, feedback_id, task_id, measurement_id, "
                        + "grid_id, status, suggested_priority, "
                        + "confirmed_priority, trigger_reason "
                        + "FROM neps.biz_anomaly_event "
                        + "WHERE measurement_id IN ("
                        + concentratedMeasurementId + ", "
                        + jumpMeasurementId + ", "
                        + highAqiMeasurementId + ") "
                        + "ORDER BY id;");
    }

    private static AssignedCase createAssignedCase(
            HttpClient citizen,
            HttpClient admin,
            long gridId,
            long assigneeId,
            String label) throws Exception {

        JsonNode feedback = postJson(
                citizen,
                "/api/feedbacks",
                Map.of(
                        "gridId", gridId,
                        "address", "检查用地址-" + label,
                        "observedAt",
                        LocalDateTime.now()
                                .minusMinutes(5)
                                .withNano(0)
                                .toString(),
                        "description", "检查用反馈-" + label),
                201);

        long feedbackId = feedback.path("id").asLong();

        JsonNode task = postJson(
                admin,
                "/api/admin/feedbacks/"
                        + feedbackId
                        + "/assign",
                Map.of(
                        "assigneeId", assigneeId,
                        "requirement", "执行" + label,
                        "priority", "MEDIUM"),
                200);

        return new AssignedCase(
                feedbackId,
                task.path("taskId").asLong());
    }

    private static void checkRuleResult(
            JsonNode measurement,
            String expectedStatus,
            String expectedPriority,
            String reasonFragment) {

        String actualStatus =
                measurement.path("ruleStatus").asText();

        if (!expectedStatus.equals(actualStatus)) {
            throw new IllegalStateException(
                    "规则状态不符合预期："
                            + measurement);
        }

        JsonNode priority =
                measurement.path("suggestedPriority");

        if (expectedPriority == null) {
            if (!priority.isNull()) {
                throw new IllegalStateException(
                        "当前规则不应产生建议优先级："
                                + measurement);
            }
        } else if (!expectedPriority.equals(
                priority.asText())) {
            throw new IllegalStateException(
                    "规则建议优先级不符合预期："
                            + measurement);
        }

        if (!measurement.path("ruleReason")
                .asText()
                .contains(reasonFragment)
                || !"DEMO-RULE-1".equals(
                measurement.path("ruleVersion").asText())
                || !measurement.path("ruleEvaluatedAt")
                .isTextual()) {

            throw new IllegalStateException(
                    "规则理由、版本或执行时间不正确："
                            + measurement);
        }
    }

    private static void checkStoredAnomalyEvent(
            long measurementId,
            boolean shouldExist,
            String expectedPriority,
            String reasonFragment) throws Exception {

        String sql = "SELECT status, suggested_priority, trigger_reason "
                + "FROM biz_anomaly_event "
                + "WHERE measurement_id = ?";

        try (Connection connection = testDatabaseConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, measurementId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    if (shouldExist) {
                        throw new IllegalStateException(
                                "没有找到检测记录对应的异常事件，measurementId="
                                        + measurementId);
                    }
                    return;
                }

                if (!shouldExist) {
                    throw new IllegalStateException(
                            "正常或质量问题记录不应生成污染事件，measurementId="
                                    + measurementId);
                }

                String status = result.getString("status");
                String priority = result.getString("suggested_priority");
                String reason = result.getString("trigger_reason");

                if (!"PENDING_REVIEW".equals(status)
                        || !expectedPriority.equals(priority)
                        || !reason.contains(reasonFragment)) {

                    throw new IllegalStateException(
                            "数据库异常事件内容不正确，measurementId="
                                    + measurementId
                                    + "，status=" + status
                                    + "，priority=" + priority
                                    + "，reason=" + reason);
                }

                if (result.next()) {
                    throw new IllegalStateException(
                            "同一检测记录生成了重复异常事件，measurementId="
                                    + measurementId);
                }
            }
        }
    }

    private static long findAnomalyEventId(
            long measurementId) throws Exception {

        try (Connection connection = testDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id FROM biz_anomaly_event "
                             + "WHERE measurement_id = ?")) {

            statement.setLong(1, measurementId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                            "找不到检测记录对应的异常事件，measurementId="
                                    + measurementId);
                }
                return result.getLong("id");
            }
        }
    }

    private static long findWorkOrderId(
            long anomalyEventId) throws Exception {

        try (Connection connection = testDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id FROM biz_work_order "
                             + "WHERE anomaly_event_id = ?")) {

            statement.setLong(1, anomalyEventId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                            "已确认异常没有生成处置工单，anomalyEventId="
                                    + anomalyEventId);
                }
                return result.getLong("id");
            }
        }
    }

    private static long findWarningId(
            long anomalyEventId) throws Exception {

        try (Connection connection = testDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id FROM biz_warning "
                             + "WHERE anomaly_event_id = ?")) {

            statement.setLong(1, anomalyEventId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                            "已确认异常没有生成内部预警，anomalyEventId="
                                    + anomalyEventId);
                }
                return result.getLong("id");
            }
        }
    }

    private static void checkWarningResponse(
            JsonNode warning,
            long expectedWarningId,
            long expectedAnomalyEventId,
            long expectedGridId,
            String expectedStatus,
            boolean shouldBeClosed) {

        boolean closedAtInvalid = shouldBeClosed
                ? !warning.path("closedAt").isTextual()
                : !warning.path("closedAt").isNull();

        if (warning.path("id").asLong()
                != expectedWarningId
                || warning.path("anomalyEventId").asLong()
                != expectedAnomalyEventId
                || warning.path("gridId").asLong()
                != expectedGridId
                || !"MEDIUM".equals(
                warning.path("warningLevel").asText())
                || !expectedStatus.equals(
                warning.path("status").asText())
                || !"污染异常预警".equals(
                warning.path("title").asText())
                || !warning.path("content").isTextual()
                || closedAtInvalid) {

            throw new IllegalStateException(
                    "内部预警响应不符合预期："
                            + warning);
        }
    }

    private static void requireUnifiedTask(
            JsonNode tasks,
            String expectedType,
            long expectedId,
            String expectedStatus,
            boolean shouldExist) {

        if (!tasks.isArray()) {
            throw new IllegalStateException(
                    "统一任务响应不是数组：" + tasks);
        }

        JsonNode matched = null;

        for (JsonNode task : tasks) {
            if (expectedType.equals(
                    task.path("taskType").asText())
                    && task.path("id").asLong()
                    == expectedId) {

                matched = task;
                break;
            }
        }

        if (!shouldExist) {
            if (matched != null) {
                throw new IllegalStateException(
                        "统一任务中不应出现该记录："
                                + matched);
            }
            return;
        }

        if (matched == null
                || !expectedStatus.equals(
                matched.path("status").asText())
                || matched.path("feedbackId").asLong() <= 0
                || matched.path("gridId").asLong() <= 0
                || !matched.path("address").isTextual()
                || !matched.path("description").isTextual()
                || !matched.path("requirement").isTextual()
                || !matched.path("priority").isTextual()
                || !matched.path("assignedAt").isTextual()) {

            throw new IllegalStateException(
                    "统一任务记录不符合预期，type="
                            + expectedType
                            + "，id="
                            + expectedId
                            + "，响应="
                            + tasks);
        }

        if ("INSPECTION".equals(expectedType)
                && !matched.path("anomalyEventId").isNull()) {

            throw new IllegalStateException(
                    "核查任务不应关联异常事件："
                            + matched);
        }

        if ("DISPOSAL".equals(expectedType)
                && matched.path("anomalyEventId").asLong() <= 0) {

            throw new IllegalStateException(
                    "处置工单缺少异常事件ID："
                            + matched);
        }
    }

    private static void checkWorkOrderResponse(
            JsonNode order,
            long expectedOrderId,
            long expectedAnomalyEventId,
            long expectedFeedbackId,
            String expectedStatus,
            Long expectedAssigneeId,
            String expectedRequirement) {

        boolean assigneeInvalid =
                expectedAssigneeId == null
                        ? !order.path("assigneeId").isNull()
                        : order.path("assigneeId").asLong()
                        != expectedAssigneeId;

        boolean requirementInvalid =
                expectedRequirement == null
                        ? !order.path("requirement").isNull()
                        : !expectedRequirement.equals(
                        order.path("requirement").asText());

        if (order.path("id").asLong() != expectedOrderId
                || order.path("anomalyEventId").asLong()
                != expectedAnomalyEventId
                || order.path("feedbackId").asLong()
                != expectedFeedbackId
                || !expectedStatus.equals(
                order.path("status").asText())
                || !"MEDIUM".equals(
                order.path("priority").asText())
                || assigneeInvalid
                || requirementInvalid
                || !order.path("address").isTextual()
                || !order.path("description").isTextual()
                || !order.path("triggerReason").isTextual()) {

            throw new IllegalStateException(
                    "处置工单响应不符合预期：" + order);
        }
    }

    private static void checkClosedWarning(
            long anomalyEventId) throws Exception {

        String sql = "SELECT status, closed_at "
                + "FROM biz_warning "
                + "WHERE anomaly_event_id = ?";

        try (Connection connection = testDatabaseConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, anomalyEventId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || !"CLOSED".equals(
                        result.getString("status"))
                        || result.getTimestamp("closed_at") == null) {

                    throw new IllegalStateException(
                            "工单关闭后预警没有同步关闭，anomalyEventId="
                                    + anomalyEventId);
                }
            }
        }
    }

    private static void checkWorkOrderReviewHistory(
            long workOrderId) throws Exception {

        String sql = "SELECT decision, measures, result, opinion, "
                + "public_reply FROM biz_work_order_review "
                + "WHERE work_order_id = ? ORDER BY id";

        try (Connection connection = testDatabaseConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, workOrderId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || !"RETURN".equals(
                        result.getString("decision"))
                        || !"对现场污染源进行临时停运，并清理散落污染物。"
                        .equals(result.getString("measures"))
                        || result.getString("public_reply") != null) {

                    throw new IllegalStateException(
                            "第一次工单退回历史不正确，workOrderId="
                                    + workOrderId);
                }

                if (!result.next()
                        || !"CLOSE".equals(
                        result.getString("decision"))
                        || !"完成现场清理，并对污染点进行覆盖和隔离。"
                        .equals(result.getString("measures"))
                        || !"污染问题已完成现场处置和复核，相关预警已经解除。"
                        .equals(result.getString("public_reply"))) {

                    throw new IllegalStateException(
                            "第二次工单关闭历史不正确，workOrderId="
                                    + workOrderId);
                }

                if (result.next()) {
                    throw new IllegalStateException(
                            "工单复核历史数量应为2，workOrderId="
                                    + workOrderId);
                }
            }
        }
    }

    private static void checkAnomalyResponse(
            JsonNode event,
            long expectedEventId,
            long expectedMeasurementId,
            String expectedStatus,
            String expectedSuggestedPriority,
            String expectedConfirmedPriority) {

        boolean confirmedPriorityInvalid =
                expectedConfirmedPriority == null
                        ? !event.path("confirmedPriority").isNull()
                        : !expectedConfirmedPriority.equals(
                        event.path("confirmedPriority").asText());

        if (event.path("id").asLong() != expectedEventId
                || event.path("measurementId").asLong()
                != expectedMeasurementId
                || !expectedStatus.equals(
                event.path("status").asText())
                || !expectedSuggestedPriority.equals(
                event.path("suggestedPriority").asText())
                || confirmedPriorityInvalid
                || !event.path("triggerReason").isTextual()
                || event.path("feedbackId").asLong() <= 0
                || event.path("taskId").asLong() <= 0
                || event.path("gridId").asLong() <= 0) {

            throw new IllegalStateException(
                    "异常事件响应不符合预期：" + event);
        }
    }

    private static Connection testDatabaseConnection()
            throws Exception {

        String url = "jdbc:mysql://localhost:3306/neps"
                + "?characterEncoding=UTF-8"
                + "&serverTimezone=Asia/Shanghai";

        return DriverManager.getConnection(
                url,
                env("DB_USERNAME"),
                env("DB_PASSWORD"));
    }

    private static boolean isAnalysisLifecycleStatus(
            String status) {

        return "PENDING".equals(status)
                || "RUNNING".equals(status)
                || "SUCCEEDED".equals(status)
                || "FAILED".equals(status);
    }

    private static void awaitMockAnalysis(
            String targetType,
            long targetId,
            int expectedAttemptCount) throws Exception {

        long deadline = System.nanoTime()
                + Duration.ofSeconds(20).toNanos();

        while (System.nanoTime() < deadline) {
            try (Connection connection = testDatabaseConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT status, provider, model_name, "
                                 + "input_snapshot, result_text, "
                                 + "failure_reason, is_demo, attempt_count, "
                                 + "started_at, completed_at, "
                                 + "(SELECT COUNT(*) FROM biz_ai_analysis "
                                 + "WHERE target_type = ? AND target_id = ?) "
                                 + "AS record_count "
                                 + "FROM biz_ai_analysis "
                                 + "WHERE target_type = ? AND target_id = ?")) {

                statement.setString(1, targetType);
                statement.setLong(2, targetId);
                statement.setString(3, targetType);
                statement.setLong(4, targetId);

                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        String status = result.getString("status");

                        if ("FAILED".equals(status)) {
                            throw new IllegalStateException(
                                    "AI初判失败，targetType="
                                            + targetType
                                            + "，targetId=" + targetId
                                            + "，原因="
                                            + result.getString(
                                            "failure_reason"));
                        }

                        if ("SUCCEEDED".equals(status)) {
                            JsonNode analysis = JSON.readTree(
                                    result.getString("result_text"));

                            boolean invalid =
                                    !"MOCK".equals(
                                            result.getString("provider"))
                                            || !"LOCAL-DEMO".equals(
                                            result.getString("model_name"))
                                            || result.getString(
                                            "input_snapshot") == null
                                            || result.getInt("is_demo") != 1
                                            || result.getInt(
                                            "attempt_count")
                                            != expectedAttemptCount
                                            || result.getTimestamp(
                                            "started_at") == null
                                            || result.getTimestamp(
                                            "completed_at") == null
                                            || result.getLong(
                                            "record_count") != 1
                                            || analysis.path("summary")
                                            .asText().isBlank()
                                            || analysis.path(
                                            "suspectedPhenomenon")
                                            .asText().isBlank()
                                            || !analysis.path("checkItems")
                                            .isArray()
                                            || analysis.path("checkItems")
                                            .isEmpty();

                            if (invalid) {
                                throw new IllegalStateException(
                                        "AI初判记录不符合预期，targetType="
                                                + targetType
                                                + "，targetId=" + targetId);
                            }
                            return;
                        }
                    }
                }
            }

            Thread.sleep(200);
        }

        throw new IllegalStateException(
                "等待AI初判超时，targetType="
                        + targetType
                        + "，targetId=" + targetId);
    }

    private static long findAnalysisId(
            String targetType,
            long targetId) throws Exception {

        try (Connection connection = testDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id FROM biz_ai_analysis "
                             + "WHERE target_type = ? AND target_id = ?")) {

            statement.setString(1, targetType);
            statement.setLong(2, targetId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                            "找不到AI初判记录，targetType="
                                    + targetType
                                    + "，targetId=" + targetId);
                }
                return result.getLong("id");
            }
        }
    }

    private static void markAnalysisAsTimedOut(
            long analysisId) throws Exception {

        try (Connection connection = testDatabaseConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE biz_ai_analysis SET status = 'FAILED', "
                             + "failure_reason = '接口测试模拟AI服务超时', "
                             + "completed_at = NOW(), updated_at = NOW() "
                             + "WHERE id = ? AND status = 'SUCCEEDED'")) {

            statement.setLong(1, analysisId);

            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "无法构造AI初判失败记录，ID=" + analysisId);
            }
        }
    }

    private static void checkAiAnalysisResponse(
            JsonNode analysis,
            long expectedId,
            String expectedTargetType,
            long expectedTargetId,
            String expectedStatus,
            int expectedAttemptCount) throws Exception {

        boolean commonInvalid =
                analysis.path("id").asLong() != expectedId
                        || !expectedTargetType.equals(
                        analysis.path("targetType").asText())
                        || analysis.path("targetId").asLong()
                        != expectedTargetId
                        || !expectedStatus.equals(
                        analysis.path("status").asText())
                        || analysis.path("attemptCount").asInt()
                        != expectedAttemptCount
                        || !analysis.path("createdAt").isTextual()
                        || !analysis.path("updatedAt").isTextual();

        if (commonInvalid) {
            throw new IllegalStateException(
                    "AI初判响应基础字段不符合预期：" + analysis);
        }

        if ("FAILED".equals(expectedStatus)) {
            if (!analysis.path("failureReason").asText()
                    .contains("模拟AI服务超时")) {
                throw new IllegalStateException(
                        "AI失败原因未正确返回：" + analysis);
            }
            return;
        }

        JsonNode result = JSON.readTree(
                analysis.path("resultText").asText());

        if (!"MOCK".equals(analysis.path("provider").asText())
                || !"LOCAL-DEMO".equals(
                analysis.path("modelName").asText())
                || !analysis.path("demo").asBoolean()
                || !analysis.path("startedAt").isTextual()
                || !analysis.path("completedAt").isTextual()
                || analysis.path("inputSnapshot").asText().isBlank()
                || result.path("summary").asText().isBlank()
                || result.path("suspectedPhenomenon")
                .asText().isBlank()
                || !result.path("checkItems").isArray()
                || result.path("checkItems").isEmpty()) {

            throw new IllegalStateException(
                    "AI成功结果不符合预期：" + analysis);
        }
    }

    private record AssignedCase(
            long feedbackId,
            long taskId) {
    }

    private static void checkReviewResponse(
            JsonNode review,
            long expectedMeasurementId,
            String expectedDecision,
            String expectedMeasurementStatus,
            String expectedTaskStatus,
            String expectedFeedbackStatus) {

        boolean invalid =
                review.path("reviewId").asLong() <= 0

                        || review.path("measurementId").asLong()
                        != expectedMeasurementId

                        || review.path("reviewerId").asLong() <= 0

                        || !expectedDecision.equals(
                        review.path("decision").asText())

                        || !expectedMeasurementStatus.equals(
                        review.path(
                                "measurementStatus").asText())

                        || !expectedTaskStatus.equals(
                        review.path("taskStatus").asText())

                        || !expectedFeedbackStatus.equals(
                        review.path(
                                "feedbackStatus").asText())

                        || !review.path("opinion").isTextual()

                        || !review.path("reviewedAt").isTextual();

        if (invalid) {
            throw new IllegalStateException(
                    "检测复核响应不符合预期："
                            + review);
        }
    }

    private static void requireTaskState(
            JsonNode tasks,
            long taskId,
            String expectedTaskStatus,
            String expectedFeedbackStatus) {

        if (!tasks.isArray()) {
            throw new IllegalStateException(
                    "任务列表响应不是数组："
                            + tasks);
        }

        for (JsonNode task : tasks) {
            if (task.path("taskId").asLong()
                    == taskId) {

                if (!expectedTaskStatus.equals(
                        task.path("taskStatus").asText())
                        || !expectedFeedbackStatus.equals(
                        task.path(
                                "feedbackStatus").asText())) {

                    throw new IllegalStateException(
                            "任务状态不符合预期："
                                    + task);
                }

                return;
            }
        }

        throw new IllegalStateException(
                "任务列表中找不到任务ID："
                        + taskId);
    }

    private static void requireTaskAbsent(
            JsonNode tasks,
            long taskId) {

        if (!tasks.isArray()) {
            throw new IllegalStateException(
                    "任务列表响应不是数组："
                            + tasks);
        }

        for (JsonNode task : tasks) {
            if (task.path("taskId").asLong()
                    == taskId) {

                throw new IllegalStateException(
                        "不应看到任务ID："
                                + taskId);
            }
        }
    }

    private static void cleanupPreviousTestData()
            throws Exception {

        String username = env("DB_USERNAME");
        String password = env("DB_PASSWORD");

        String url =
                "jdbc:mysql://localhost:3306/neps"
                        + "?characterEncoding=UTF-8"
                        + "&serverTimezone=Asia/Shanghai";

        String testPhones =
                "'18800000001',"
                        + "'18800000002',"
                        + "'18800000003',"
                        + "'18800000004',"
                        + "'18800000005'";

        String testNames =
                "'检查用公众',"
                        + "'第二名检查用公众',"
                        + "'检查用网格员',"
                        + "'第二名检查网格员',"
                        + "'检查用决策者'";

        String userIds =
                "SELECT id FROM sys_user "
                        + "WHERE phone IN (" + testPhones + ") "
                        + "AND display_name IN (" + testNames + ") "
                        + "AND role <> 'ADMIN'";

        String feedbackIds =
                "SELECT id FROM biz_feedback "
                        + "WHERE submitter_id IN ("
                        + userIds + ")";

        List<String> attachmentPaths =
                new ArrayList<>();

        try (Connection connection =
                     DriverManager.getConnection(
                             url, username, password);
             Statement statement =
                     connection.createStatement()) {

            connection.setAutoCommit(false);

            try {
                // 固定测试号码绝对不能对应管理员。
                try (ResultSet result = statement.executeQuery(
                        "SELECT COUNT(*) "
                                + "FROM sys_user "
                                + "WHERE phone IN ("
                                + testPhones + ") "
                                + "AND role = 'ADMIN'")) {

                    result.next();

                    if (result.getLong(1) > 0) {
                        throw new IllegalStateException(
                                "固定测试手机号中存在管理员，拒绝清理");
                    }
                }

                try (ResultSet result = statement.executeQuery(
                        "SELECT storage_path "
                                + "FROM biz_attachment "
                                + "WHERE uploader_id IN ("
                                + userIds + ") "
                                + "OR (business_type = 'FEEDBACK' "
                                + "AND business_id IN ("
                                + feedbackIds + "))")) {

                    while (result.next()) {
                        attachmentPaths.add(
                                result.getString("storage_path"));
                    }
                }

                statement.executeUpdate(
                        "DELETE FROM biz_operation_log "
                                + "WHERE operator_id IN ("
                                + userIds + ") "
                                + "OR from_assignee_id IN ("
                                + userIds + ") "
                                + "OR to_assignee_id IN ("
                                + userIds + ") "
                                + "OR (business_type = 'FEEDBACK' "
                                + "AND business_id IN ("
                                + feedbackIds + "))");

                statement.executeUpdate(
                        "DELETE FROM biz_attachment "
                                + "WHERE uploader_id IN ("
                                + userIds + ") "
                                + "OR (business_type = 'FEEDBACK' "
                                + "AND business_id IN ("
                                + feedbackIds + "))");

                statement.executeUpdate(
                        "DELETE FROM biz_anomaly_event "
                                + "WHERE feedback_id IN ("
                                + feedbackIds + ") "
                                + "OR measurement_id IN ("
                                + "SELECT id FROM biz_measurement "
                                + "WHERE feedback_id IN ("
                                + feedbackIds + ") "
                                + "OR submitter_id IN ("
                                + userIds + "))");

                statement.executeUpdate(
                        "DELETE FROM biz_measurement_review "
                                + "WHERE measurement_id IN ("
                                + "SELECT id FROM biz_measurement "
                                + "WHERE feedback_id IN ("
                                + feedbackIds + ") "
                                + "OR submitter_id IN ("
                                + userIds + "))");

                statement.executeUpdate(
                        "DELETE FROM biz_ai_analysis "
                                + "WHERE target_type = 'MEASUREMENT' "
                                + "AND target_id IN ("
                                + "SELECT id FROM biz_measurement "
                                + "WHERE feedback_id IN ("
                                + feedbackIds + ") "
                                + "OR submitter_id IN ("
                                + userIds + "))");

                statement.executeUpdate(
                        "DELETE FROM biz_measurement "
                                + "WHERE feedback_id IN ("
                                + feedbackIds + ") "
                                + "OR submitter_id IN ("
                                + userIds + ")");

                statement.executeUpdate(
                        "DELETE FROM biz_inspection_task "
                                + "WHERE feedback_id IN ("
                                + feedbackIds + ") "
                                + "OR assignee_id IN ("
                                + userIds + ") "
                                + "OR assigned_by IN ("
                                + userIds + ")");

                statement.executeUpdate(
                        "DELETE FROM biz_ai_analysis "
                                + "WHERE target_type = 'FEEDBACK' "
                                + "AND target_id IN ("
                                + feedbackIds + ")");

                statement.executeUpdate(
                        "DELETE f "
                                + "FROM biz_feedback f "
                                + "JOIN sys_user u "
                                + "ON u.id = f.submitter_id "
                                + "WHERE u.phone IN ("
                                + testPhones + ") "
                                + "AND u.display_name IN ("
                                + testNames + ") "
                                + "AND u.role <> 'ADMIN'");

                statement.executeUpdate(
                        "DELETE FROM sys_user_grid "
                                + "WHERE user_id IN ("
                                + userIds + ")");

                statement.executeUpdate(
                        "DELETE FROM sys_user_region "
                                + "WHERE user_id IN ("
                                + userIds + ")");

                statement.executeUpdate(
                        "DELETE FROM sys_user "
                                + "WHERE phone IN ("
                                + testPhones + ") "
                                + "AND display_name IN ("
                                + testNames + ") "
                                + "AND role <> 'ADMIN'");

                connection.commit();

                Path uploadRoot = Path.of(
                                System.getenv().getOrDefault(
                                        "UPLOAD_DIR",
                                        "./uploads"))
                        .toAbsolutePath()
                        .normalize();

                for (String relativePath : attachmentPaths) {
                    Path file = uploadRoot
                            .resolve(relativePath)
                            .normalize();

                    if (!file.startsWith(uploadRoot)) {
                        System.out.println(
                                "跳过异常测试附件路径："
                                        + relativePath);
                        continue;
                    }

                    try {
                        Files.deleteIfExists(file);
                    } catch (Exception exception) {
                        System.out.println(
                                "测试附件文件清理失败："
                                        + file);
                    }
                }

                System.out.println(
                        "上一轮固定账号的测试数据已清理");
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static String env(String name) {
        return Objects.requireNonNull(
                System.getenv(name), "请配置环境变量：" + name);
    }

    private static void requireRecordId(
            JsonNode body,
            long expectedId,
            boolean shouldExist) {

        if (!body.isArray()) {
            throw new IllegalStateException(
                    "预期返回数组：" + body);
        }

        boolean exists = false;

        for (JsonNode item : body) {
            if (item.path("id").asLong() == expectedId) {
                exists = true;
                break;
            }
        }

        if (exists != shouldExist) {
            throw new IllegalStateException(
                    "记录ID检查失败，ID="
                            + expectedId
                            + "，预期存在="
                            + shouldExist
                            + "，实际响应="
                            + body);
        }
    }

    private static Map<String, Object> measurementRequest(
            String reportType,
            boolean statisticallyValid,
            String invalidReason) {

        Map<String, Object> request =
                new HashMap<>();

        request.put(
                "measuredAt",
                LocalDateTime.now()
                        .minusMinutes(10)
                        .withNano(0)
                        .toString());

        request.put(
                "location",
                "演示区环保路12号附近");

        request.put(
                "reportType",
                reportType);

        request.put(
                "dataSource",
                "检查用空气质量检测仪");

        /*
         * 这些数据会使：
         * SO2 IAQI = 100
         * PM10 IAQI = 100
         * 其余四项 IAQI = 50
         * 最终 AQI = 100
         * 首要污染物为 SO2、PM10
         */
        request.put("so2", 150);
        request.put("no2", 40);
        request.put("co", 2.0);
        request.put("o3", 100);
        request.put("pm10", 120);
        request.put("pm25", 35);

        request.put(
                "missingReason",
                null);

        request.put(
                "statisticallyValid",
                statisticallyValid);

        request.put(
                "invalidReason",
                invalidReason);

        request.put(
                "siteNote",
                "现场有轻微异味，已完成六项污染物检测");

        return request;
    }

    private static void checkDailyMeasurement(
            JsonNode measurement,
            long expectedTaskId,
            long expectedFeedbackId,
            int expectedVersion) {

        boolean invalid =
                measurement.path("id").asLong() <= 0

                        || measurement.path("taskId").asLong()
                        != expectedTaskId

                        || measurement.path("feedbackId").asLong()
                        != expectedFeedbackId

                        || measurement.path("versionNo").asInt()
                        != expectedVersion

                        || !"DAILY".equals(
                        measurement.path("reportType").asText())

                        || !"VALID".equals(
                        measurement.path("qualityFlag").asText())

                        || !measurement.path(
                        "statisticallyValid").asBoolean()

                        || !measurement.path(
                        "aqiCalculable").asBoolean()

                        || measurement.path("so2Iaqi").asInt()
                        != 100

                        || measurement.path("no2Iaqi").asInt()
                        != 50

                        || measurement.path("coIaqi").asInt()
                        != 50

                        || measurement.path("o3Iaqi").asInt()
                        != 50

                        || measurement.path("pm10Iaqi").asInt()
                        != 100

                        || measurement.path("pm25Iaqi").asInt()
                        != 50

                        || measurement.path("aqi").asInt()
                        != 100

                        || measurement.path("aqiLevel").asInt()
                        != 2

                        || !"良".equals(
                        measurement.path("aqiCategory").asText())

                        || !"PENDING".equals(
                        measurement.path("reviewStatus").asText())

                        || !"HJ 633-2026".equals(
                        measurement.path(
                                "standardVersion").asText())

                        || !measurement.path(
                        "submittedAt").isTextual();

        if (invalid) {
            throw new IllegalStateException(
                    "日报检测响应不符合预期："
                            + measurement);
        }

        String primary =
                measurement.path(
                        "primaryPollutants").asText();

        List<String> pollutants =
                Arrays.asList(primary.split(","));

        if (pollutants.size() != 2
                || !pollutants.contains("SO2")
                || !pollutants.contains("PM10")) {

            throw new IllegalStateException(
                    "并列首要污染物不符合预期："
                            + primary);
        }

        if (!measurement.path(
                "calculationReason").isNull()) {

            throw new IllegalStateException(
                    "可以计算AQI时不应存在失败原因");
        }
    }

    private static void checkInstantMeasurement(
            JsonNode measurement,
            long expectedTaskId,
            long expectedFeedbackId) {

        boolean invalid =
                measurement.path("id").asLong() <= 0

                        || measurement.path("taskId").asLong()
                        != expectedTaskId

                        || measurement.path("feedbackId").asLong()
                        != expectedFeedbackId

                        || !"INSTANT".equals(
                        measurement.path("reportType").asText())

                        || measurement.path(
                        "statisticallyValid").asBoolean()

                        || !"INVALID".equals(
                        measurement.path("qualityFlag").asText())

                        || measurement.path(
                        "aqiCalculable").asBoolean()

                        || !measurement.path("aqi").isNull()

                        || !measurement.path("aqiLevel").isNull()

                        || !measurement.path(
                        "aqiCategory").isNull()

                        || !measurement.path(
                        "primaryPollutants").isNull()

                        || !measurement.path("so2Iaqi").isNull()

                        || !measurement.path("pm10Iaqi").isNull()

                        || !"PENDING".equals(
                        measurement.path("reviewStatus").asText());

        if (invalid) {
            throw new IllegalStateException(
                    "瞬时检测响应不符合预期："
                            + measurement);
        }

        String reason =
                measurement.path(
                        "calculationReason").asText();

        if (!reason.contains("瞬时读数")) {
            throw new IllegalStateException(
                    "瞬时读数的AQI失败原因不正确："
                            + measurement);
        }
    }

    private static HttpClient newClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(
                        null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    private static void login(
            HttpClient client, String phone, String password) throws Exception {
        String form = "phone="
                + URLEncoder.encode(phone, StandardCharsets.UTF_8)
                + "&password="
                + URLEncoder.encode(password, StandardCharsets.UTF_8);

        post(client, "/api/auth/login",
                "application/x-www-form-urlencoded", form, 204);
    }

    private static byte[] createTestPng() throws Exception {
        BufferedImage image =
                new BufferedImage(
                        2,
                        2,
                        BufferedImage.TYPE_INT_RGB);

        image.setRGB(0, 0, 0x00FF0000);
        image.setRGB(1, 0, 0x0000FF00);
        image.setRGB(0, 1, 0x000000FF);
        image.setRGB(1, 1, 0x00FFFFFF);

        try (ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException(
                        "无法生成PNG测试图片");
            }

            return output.toByteArray();
        }
    }

    private static JsonNode postMultipart(
            HttpClient client,
            String path,
            String filename,
            String declaredContentType,
            byte[] fileBytes,
            int expected) throws Exception {

        HttpRequest csrfRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/auth/csrf"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        var csrfResponse = client.send(
                csrfRequest,
                HttpResponse.BodyHandlers.ofString());

        if (csrfResponse.statusCode() != 200) {
            throw new IllegalStateException(
                    "获取CSRF令牌失败");
        }

        JsonNode csrf =
                JSON.readTree(csrfResponse.body());

        String boundary =
                "----NepsBoundary" + System.nanoTime();

        String partHeader =
                "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; "
                        + "name=\"file\"; "
                        + "filename=\"" + filename + "\"\r\n"
                        + "Content-Type: "
                        + declaredContentType + "\r\n\r\n";

        String partFooter =
                "\r\n--" + boundary + "--\r\n";

        byte[] requestBody;

        try (ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            output.write(
                    partHeader.getBytes(
                            StandardCharsets.UTF_8));

            output.write(fileBytes);

            output.write(
                    partFooter.getBytes(
                            StandardCharsets.UTF_8));

            requestBody = output.toByteArray();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(10))
                .header(
                        "Content-Type",
                        "multipart/form-data; boundary="
                                + boundary)
                .header(
                        csrf.path("headerName").asText(),
                        csrf.path("token").asText())
                .POST(HttpRequest.BodyPublishers
                        .ofByteArray(requestBody))
                .build();

        var response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != expected) {
            throw new IllegalStateException(
                    path + " 预期 " + expected
                            + "，实际 "
                            + response.statusCode()
                            + "，响应："
                            + response.body());
        }

        System.out.println(
                "通过：" + path + " → " + expected);

        return JSON.readTree(
                response.body().isBlank()
                        ? "null"
                        : response.body());
    }

    private static JsonNode postJson(
            HttpClient client, String path,
            Map<String, ?> body, int expected) throws Exception {
        return post(client, path, "application/json",
                JSON.writeValueAsString(body), expected);
    }

    private static JsonNode write(
            HttpClient client, String method, String path, String contentType,
            String body, int expected) throws Exception {

        HttpRequest csrfRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/auth/csrf"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        var csrfResponse = client.send(
                csrfRequest, HttpResponse.BodyHandlers.ofString());

        if (csrfResponse.statusCode() != 200) {
            throw new IllegalStateException("获取 CSRF 令牌失败");
        }

        JsonNode csrf = JSON.readTree(csrfResponse.body());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", contentType)
                .header(
                        csrf.path("headerName").asText(),
                        csrf.path("token").asText())
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();

        var response = client.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != expected) {
            throw new IllegalStateException(
                    path + " 预期 " + expected
                            + "，实际 " + response.statusCode()
                            + "，响应：" + response.body());
        }

        System.out.println("通过：" + path + " → " + expected);

        return JSON.readTree(
                response.body().isBlank() ? "null" : response.body());
    }

    private static byte[] getBytes(
            HttpClient client,
            String path,
            int expected) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        var response = client.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != expected) {
            throw new IllegalStateException(
                    path + " 预期 " + expected
                            + "，实际 "
                            + response.statusCode());
        }

        System.out.println(
                "通过：" + path + " → " + expected);

        return response.body();
    }

    private static JsonNode get(
            HttpClient client, String path, int expected) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        var response = client.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != expected) {
            throw new IllegalStateException(
                    path + " 预期 " + expected
                            + "，实际 " + response.statusCode()
                            + "，响应：" + response.body());
        }

        System.out.println("通过：" + path + " → " + expected);
        return JSON.readTree(response.body());
    }

    private static JsonNode post(
            HttpClient client, String path, String contentType,
            String body, int expected) throws Exception {
        return write(client, "POST", path, contentType, body, expected);
    }

    private static JsonNode delete(
            HttpClient client, String path, int expected) throws Exception {
        return write(client, "DELETE", path, "application/json", "", expected);
    }

    private static void checkIds(JsonNode body, Long... expected) {
        if (!body.isArray()) {
            throw new IllegalStateException("预期返回记录数组：" + body);
        }

        java.util.Set<Long> actual = new java.util.HashSet<>();

        for (JsonNode item : body) {
            if (!item.path("id").isIntegralNumber()) {
                throw new IllegalStateException("记录 ID 格式错误：" + item);
            }
            actual.add(item.path("id").asLong());
        }

        if (body.size() != expected.length
                || !actual.equals(java.util.Set.of(expected))) {
            throw new IllegalStateException("负责记录不符合预期：" + body);
        }
    }

    private static void checkInspectionTask(
            JsonNode task,
            long expectedFeedbackId,
            long expectedGridId,
            long expectedAssigneeId) {

        boolean invalid =
                task.path("taskId").asLong() <= 0
                        || task.path("feedbackId").asLong()
                        != expectedFeedbackId
                        || task.path("gridId").asLong()
                        != expectedGridId
                        || task.path("assigneeId").asLong()
                        != expectedAssigneeId
                        || !"CHECKING".equals(
                        task.path("feedbackStatus").asText())
                        || !"PENDING".equals(
                        task.path("taskStatus").asText())
                        || !"HIGH".equals(
                        task.path("priority").asText())
                        || !"请到现场核查异味来源并拍照记录".equals(
                        task.path("requirement").asText())
                        || !task.path("assignedAt").isTextual();

        if (invalid) {
            throw new IllegalStateException(
                    "核查任务响应不符合预期：" + task);
        }
    }

    private static void checkFeedback(
            JsonNode feedback,
            long expectedGridId,
            String expectedAddress,
            String expectedDescription) {

        boolean invalid =
                feedback.path("id").asLong() <= 0
                        || feedback.path("gridId").asLong() != expectedGridId
                        || !expectedAddress.equals(
                        feedback.path("address").asText())
                        || !expectedDescription.equals(
                        feedback.path("description").asText())
                        || !"PENDING_ASSIGN".equals(
                        feedback.path("status").asText())
                        || !isAnalysisLifecycleStatus(
                        feedback.path("analysisStatus").asText())
                        || !feedback.has("publicReply")
                        || !feedback.path("publicReply").isNull()
                        || !feedback.path("createdAt").isTextual()
                        || !feedback.path("updatedAt").isTextual()
                        || feedback.has("submitterId");

        if (invalid) {
            throw new IllegalStateException(
                    "反馈响应内容不符合预期：" + feedback);
        }
    }

    private static void checkUser(JsonNode user, String role) {
        if (user.path("id").asLong() <= 0
                || !role.equals(user.path("role").asText())
                || user.has("password")
                || user.has("passwordHash")
                || user.has("password_hash")) {
            throw new IllegalStateException("用户响应内容不符合预期");
        }
    }
}
