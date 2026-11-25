package com.gym.springapp.automation;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.gym.springapp.automation.entity.AutomationStatus;
import com.gym.springapp.automation.entity.AutomationType;
import com.gym.springapp.model.Member;
import com.gym.springapp.model.Payment;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.PaymentRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GoogleSheetsService {

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsService.class);
    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private static final String TAB_MEMBERS = "Members";
    private static final String TAB_PAYMENTS = "Payments";
    private static final String TAB_DAILY_REPORT = "Daily Report";
    private static final String TAB_CHARTS = "Charts";

    private static final List<Object> MEMBERS_HEADER = Arrays.asList(
            "Member ID", "Name", "Email", "Phone", "Gender", "Plan", "Trainer",
            "Join Date", "Expiry Date", "Days Left", "Status", "Total Paid (₹)",
            "Last Payment Date", "Registered On", "Last Updated"
    );

    private static final List<Object> PAYMENTS_HEADER = Arrays.asList(
            "Payment ID", "Member ID", "Member Name", "Plan", "Amount (₹)",
            "Mode", "Status", "Payment Date", "Invoice No"
    );

    private static final List<Object> DAILY_REPORT_HEADER = Arrays.asList(
            "Date", "Total Members", "Active", "Expired", "Pending Payment",
            "New Today", "Revenue Today (₹)", "Revenue This Month (₹)"
    );

    private final AutomationProperties automationProperties;
    private final AutomationLogService automationLogService;
    private final MemberRepo memberRepo;
    private final PaymentRepo paymentRepo;

    private volatile Sheets sheetsClient;

    public GoogleSheetsService(AutomationProperties automationProperties,
                               AutomationLogService automationLogService,
                               MemberRepo memberRepo,
                               PaymentRepo paymentRepo) {
        this.automationProperties = automationProperties;
        this.automationLogService = automationLogService;
        this.memberRepo = memberRepo;
        this.paymentRepo = paymentRepo;
    }

    public boolean isConfigured() {
        if (!automationProperties.isSheetsConfigured()) {
            return false;
        }
        InputStream stream = getCredentialsStream();
        if (stream == null) {
            log.info("Google Sheets credentials file not found on filesystem or classpath. Automation marked as Not Configured.");
            return false;
        }
        try {
            stream.close();
        } catch (Exception ignored) {}
        return true;
    }

    private InputStream getCredentialsStream() {
        // 1. Read credentials from file path in env GOOGLE_CREDENTIALS_PATH (e.g. /etc/secrets/google-credentials.json)
        String envPath = System.getenv("GOOGLE_CREDENTIALS_PATH");
        if (envPath != null && !envPath.trim().isEmpty()) {
            File f = new File(envPath.trim());
            if (f.exists() && f.isFile()) {
                try {
                    log.info("Loading Google credentials from GOOGLE_CREDENTIALS_PATH: {}", envPath);
                    return new FileInputStream(f);
                } catch (Exception e) {
                    log.warn("Failed to read credentials from GOOGLE_CREDENTIALS_PATH {}: {}", envPath, e.getMessage());
                }
            }
        }

        // 2. Read credentials from configured property file path
        String credsFile = automationProperties.getSheets().getCredentialsFile();
        if (credsFile != null && !credsFile.trim().isEmpty()) {
            File f = new File(credsFile.trim());
            if (f.exists() && f.isFile()) {
                try {
                    log.info("Loading Google credentials from filesystem path: {}", credsFile);
                    return new FileInputStream(f);
                } catch (Exception e) {
                    log.warn("Failed to read credentials from filesystem path {}: {}", credsFile, e.getMessage());
                }
            }
            InputStream cpStream = getClass().getClassLoader().getResourceAsStream(credsFile.trim());
            if (cpStream != null) {
                log.info("Loading Google credentials from classpath: {}", credsFile);
                return cpStream;
            }
        }

        // 3. Fallback to classpath google-credentials.json
        InputStream defaultCpStream = getClass().getClassLoader().getResourceAsStream("google-credentials.json");
        if (defaultCpStream != null) {
            log.info("Loading Google credentials from default classpath: google-credentials.json");
            return defaultCpStream;
        }

        // 4. Fallback to local root file google-credentials.json
        File defaultFile = new File("google-credentials.json");
        if (defaultFile.exists() && defaultFile.isFile()) {
            try {
                return new FileInputStream(defaultFile);
            } catch (Exception e) {
                log.warn("Failed to read local google-credentials.json: {}", e.getMessage());
            }
        }

        return null;
    }

    private synchronized Sheets getSheetsClient() throws Exception {
        if (sheetsClient != null) {
            return sheetsClient;
        }

        InputStream stream = getCredentialsStream();
        if (stream == null) {
            throw new IllegalStateException("Google credentials file not found on filesystem or classpath. Google Sheets marked as Not configured.");
        }

        GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS));

        sheetsClient = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("BeastX Gym Automation")
                .build();

        return sheetsClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async("automationExecutor")
    public void onStartup() {
        if (!isConfigured()) {
            automationLogService.log(AutomationType.SHEET_SYNC, "Startup Setup",
                    AutomationStatus.SKIPPED, "Google Sheets disabled or credentials file not configured");
            return;
        }

        try {
            log.info("Initializing Google Sheets automation tabs and headers...");
            ensureTabsAndHeaders();
            fullResync();
        } catch (Exception e) {
            log.error("Failed during Google Sheets startup initialization: {}", e.getMessage(), e);
            automationLogService.log(AutomationType.SHEET_SYNC, "Startup Setup",
                    AutomationStatus.FAILED, e.getMessage());
        }
    }

    public synchronized void ensureTabsAndHeaders() {
        if (!isConfigured()) {
            automationLogService.log(AutomationType.SHEET_SYNC, "Tab Verification",
                    AutomationStatus.SKIPPED, "Google Sheets disabled or credentials file not configured");
            return;
        }

        String spreadsheetId = automationProperties.getSheets().getSpreadsheetId();
        try {
            Sheets client = getSheetsClient();
            Spreadsheet spreadsheet = client.spreadsheets().get(spreadsheetId).execute();

            Map<String, Integer> existingSheets = new HashMap<>();
            for (Sheet sheet : spreadsheet.getSheets()) {
                existingSheets.put(sheet.getProperties().getTitle(), sheet.getProperties().getSheetId());
            }

            List<Request> setupRequests = new ArrayList<>();
            boolean chartsNewlyCreated = false;

            // 1. Ensure "Members" tab
            Integer membersSheetId = existingSheets.get(TAB_MEMBERS);
            if (membersSheetId == null) {
                membersSheetId = createSheet(client, spreadsheetId, TAB_MEMBERS);
                setupRequests.addAll(createHeaderAndFreezeRequests(membersSheetId));
                setupRequests.addAll(createStatusConditionalFormattingRequests(membersSheetId));
            }

            // 2. Ensure "Payments" tab
            Integer paymentsSheetId = existingSheets.get(TAB_PAYMENTS);
            if (paymentsSheetId == null) {
                paymentsSheetId = createSheet(client, spreadsheetId, TAB_PAYMENTS);
                setupRequests.addAll(createHeaderAndFreezeRequests(paymentsSheetId));
            }

            // 3. Ensure "Daily Report" tab
            Integer dailyReportSheetId = existingSheets.get(TAB_DAILY_REPORT);
            if (dailyReportSheetId == null) {
                dailyReportSheetId = createSheet(client, spreadsheetId, TAB_DAILY_REPORT);
                setupRequests.addAll(createHeaderAndFreezeRequests(dailyReportSheetId));
            }

            // 4. Ensure "Charts" tab (created after the other tabs)
            Integer chartsSheetId = existingSheets.get(TAB_CHARTS);
            if (chartsSheetId == null) {
                chartsSheetId = createSheet(client, spreadsheetId, TAB_CHARTS);
                chartsNewlyCreated = true;
            }

            if (!setupRequests.isEmpty()) {
                BatchUpdateSpreadsheetRequest batchReq = new BatchUpdateSpreadsheetRequest().setRequests(setupRequests);
                client.spreadsheets().batchUpdate(spreadsheetId, batchReq).execute();
            }

            // Populate headers in values API if not already present
            ensureHeaderValues(client, spreadsheetId, TAB_MEMBERS, MEMBERS_HEADER);
            ensureHeaderValues(client, spreadsheetId, TAB_PAYMENTS, PAYMENTS_HEADER);
            ensureHeaderValues(client, spreadsheetId, TAB_DAILY_REPORT, DAILY_REPORT_HEADER);

            // Populate Charts formulas and native charts if newly created
            if (chartsNewlyCreated) {
                setupChartsTab(client, spreadsheetId, chartsSheetId, dailyReportSheetId);
            }

            automationLogService.log(AutomationType.SHEET_SYNC, "Ensure Tabs & Headers",
                    AutomationStatus.SUCCESS, "Google Sheet tabs and formatting verified successfully");
        } catch (Exception e) {
            log.error("Error verifying Google Sheet tabs: {}", e.getMessage(), e);
            automationLogService.log(AutomationType.SHEET_SYNC, "Ensure Tabs & Headers",
                    AutomationStatus.FAILED, e.getMessage());
        }
    }

    private Integer createSheet(Sheets client, String spreadsheetId, String title) throws Exception {
        AddSheetRequest addSheet = new AddSheetRequest()
                .setProperties(new SheetProperties().setTitle(title));
        BatchUpdateSpreadsheetRequest req = new BatchUpdateSpreadsheetRequest()
                .setRequests(Collections.singletonList(new Request().setAddSheet(addSheet)));
        BatchUpdateSpreadsheetResponse resp = client.spreadsheets().batchUpdate(spreadsheetId, req).execute();
        return resp.getReplies().get(0).getAddSheet().getProperties().getSheetId();
    }

    private List<Request> createHeaderAndFreezeRequests(Integer sheetId) {
        List<Request> requests = new ArrayList<>();
        // Freeze header row 1
        requests.add(new Request().setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                .setProperties(new SheetProperties()
                        .setSheetId(sheetId)
                        .setGridProperties(new GridProperties().setFrozenRowCount(1)))
                .setFields("gridProperties.frozenRowCount")));

        // Bold row 1
        requests.add(new Request().setRepeatCell(new RepeatCellRequest()
                .setRange(new GridRange().setSheetId(sheetId).setStartRowIndex(0).setEndRowIndex(1))
                .setCell(new CellData().setUserEnteredFormat(new CellFormat()
                        .setTextFormat(new TextFormat().setBold(true))))
                .setFields("userEnteredFormat.textFormat.bold")));

        return requests;
    }

    private List<Request> createStatusConditionalFormattingRequests(Integer membersSheetId) {
        List<Request> requests = new ArrayList<>();
        // Column index 10 = Column K ("Status")
        GridRange statusColRange = new GridRange()
                .setSheetId(membersSheetId)
                .setStartRowIndex(1)
                .setStartColumnIndex(10)
                .setEndColumnIndex(11);

        // ACTIVE -> light green (r:0.85, g:0.95, b:0.85)
        requests.add(new Request().setAddConditionalFormatRule(new AddConditionalFormatRuleRequest()
                .setRule(new ConditionalFormatRule()
                        .setRanges(Collections.singletonList(statusColRange))
                        .setBooleanRule(new BooleanRule()
                                .setCondition(new BooleanCondition()
                                        .setType("TEXT_EQ")
                                        .setValues(Collections.singletonList(new ConditionValue().setUserEnteredValue("ACTIVE"))))
                                .setFormat(new CellFormat().setBackgroundColor(new Color().setRed(0.85f).setGreen(0.95f).setBlue(0.85f)))))
                .setIndex(0)));

        // EXPIRED -> light red (r:0.95, g:0.85, b:0.85)
        requests.add(new Request().setAddConditionalFormatRule(new AddConditionalFormatRuleRequest()
                .setRule(new ConditionalFormatRule()
                        .setRanges(Collections.singletonList(statusColRange))
                        .setBooleanRule(new BooleanRule()
                                .setCondition(new BooleanCondition()
                                        .setType("TEXT_EQ")
                                        .setValues(Collections.singletonList(new ConditionValue().setUserEnteredValue("EXPIRED"))))
                                .setFormat(new CellFormat().setBackgroundColor(new Color().setRed(0.95f).setGreen(0.85f).setBlue(0.85f)))))
                .setIndex(1)));

        // PENDING_PAYMENT -> light yellow (r:0.98, g:0.95, b:0.80)
        requests.add(new Request().setAddConditionalFormatRule(new AddConditionalFormatRuleRequest()
                .setRule(new ConditionalFormatRule()
                        .setRanges(Collections.singletonList(statusColRange))
                        .setBooleanRule(new BooleanRule()
                                .setCondition(new BooleanCondition()
                                        .setType("TEXT_EQ")
                                        .setValues(Collections.singletonList(new ConditionValue().setUserEnteredValue("PENDING_PAYMENT"))))
                                .setFormat(new CellFormat().setBackgroundColor(new Color().setRed(0.98f).setGreen(0.95f).setBlue(0.80f)))))
                .setIndex(2)));

        return requests;
    }

    private void ensureHeaderValues(Sheets client, String spreadsheetId, String tabName, List<Object> header) throws Exception {
        ValueRange existing = client.spreadsheets().values()
                .get(spreadsheetId, tabName + "!A1:Z1")
                .execute();

        if (existing.getValues() == null || existing.getValues().isEmpty()) {
            ValueRange headerBody = new ValueRange().setValues(Collections.singletonList(header));
            client.spreadsheets().values()
                    .update(spreadsheetId, tabName + "!A1", headerBody)
                    .setValueInputOption("USER_ENTERED")
                    .execute();
        }
    }

    private void setupChartsTab(Sheets client, String spreadsheetId, Integer chartsSheetId, Integer dailyReportSheetId) {
        try {
            // Write summary table in Charts!A1:B4
            List<List<Object>> summary = Arrays.asList(
                    Arrays.asList("Status", "Count"),
                    Arrays.asList("ACTIVE", "=COUNTIF(Members!K2:K, \"ACTIVE\")"),
                    Arrays.asList("EXPIRED", "=COUNTIF(Members!K2:K, \"EXPIRED\")"),
                    Arrays.asList("PENDING_PAYMENT", "=COUNTIF(Members!K2:K, \"PENDING_PAYMENT\")")
            );
            ValueRange body = new ValueRange().setValues(summary);
            client.spreadsheets().values()
                    .update(spreadsheetId, TAB_CHARTS + "!A1", body)
                    .setValueInputOption("USER_ENTERED")
                    .execute();

            // Bold summary header
            List<Request> chartRequests = new ArrayList<>();
            chartRequests.add(new Request().setRepeatCell(new RepeatCellRequest()
                    .setRange(new GridRange().setSheetId(chartsSheetId).setStartRowIndex(0).setEndRowIndex(1).setStartColumnIndex(0).setEndColumnIndex(2))
                    .setCell(new CellData().setUserEnteredFormat(new CellFormat().setTextFormat(new TextFormat().setBold(true))))
                    .setFields("userEnteredFormat.textFormat.bold")));

            // Chart 1: Pie chart of status counts
            ChartData domainData = new ChartData().setSourceRange(new ChartSourceRange().setSources(
                    Collections.singletonList(new GridRange()
                            .setSheetId(chartsSheetId)
                            .setStartRowIndex(1).setEndRowIndex(4)
                            .setStartColumnIndex(0).setEndColumnIndex(1))));

            ChartData seriesData = new ChartData().setSourceRange(new ChartSourceRange().setSources(
                    Collections.singletonList(new GridRange()
                            .setSheetId(chartsSheetId)
                            .setStartRowIndex(1).setEndRowIndex(4)
                            .setStartColumnIndex(1).setEndColumnIndex(2))));

            PieChartSpec pieSpec = new PieChartSpec()
                    .setDomain(domainData)
                    .setSeries(seriesData)
                    .setPieHole(0.3);

            EmbeddedChart pieChart = new EmbeddedChart()
                    .setSpec(new ChartSpec()
                            .setTitle("Member Status Distribution")
                            .setPieChart(pieSpec))
                    .setPosition(new EmbeddedObjectPosition()
                            .setOverlayPosition(new OverlayPosition()
                                    .setAnchorCell(new GridCoordinate()
                                            .setSheetId(chartsSheetId)
                                            .setRowIndex(5).setColumnIndex(0))
                                    .setWidthPixels(500).setHeightPixels(320)));

            chartRequests.add(new Request().setAddChart(new AddChartRequest().setChart(pieChart)));

            // Chart 2: Line chart from "Daily Report" (Active, Expired, Pending over time)
            if (dailyReportSheetId != null) {
                BasicChartSpec lineSpec = new BasicChartSpec()
                        .setChartType("LINE")
                        .setLegendPosition("RIGHT_LEGEND")
                        .setDomains(Collections.singletonList(new BasicChartDomain().setDomain(
                                new ChartData().setSourceRange(new ChartSourceRange().setSources(
                                        Collections.singletonList(new GridRange()
                                                .setSheetId(dailyReportSheetId)
                                                .setStartRowIndex(0)
                                                .setStartColumnIndex(0).setEndColumnIndex(1)))))))
                        .setSeries(Arrays.asList(
                                new BasicChartSeries().setTargetAxis("LEFT_AXIS").setSeries(new ChartData().setSourceRange(new ChartSourceRange().setSources(
                                        Collections.singletonList(new GridRange()
                                                .setSheetId(dailyReportSheetId)
                                                .setStartRowIndex(0)
                                                .setStartColumnIndex(2).setEndColumnIndex(3))))),
                                new BasicChartSeries().setTargetAxis("LEFT_AXIS").setSeries(new ChartData().setSourceRange(new ChartSourceRange().setSources(
                                        Collections.singletonList(new GridRange()
                                                .setSheetId(dailyReportSheetId)
                                                .setStartRowIndex(0)
                                                .setStartColumnIndex(3).setEndColumnIndex(4))))),
                                new BasicChartSeries().setTargetAxis("LEFT_AXIS").setSeries(new ChartData().setSourceRange(new ChartSourceRange().setSources(
                                        Collections.singletonList(new GridRange()
                                                .setSheetId(dailyReportSheetId)
                                                .setStartRowIndex(0)
                                                .setStartColumnIndex(4).setEndColumnIndex(5)))))
                        ));

                EmbeddedChart lineChart = new EmbeddedChart()
                        .setSpec(new ChartSpec()
                                .setTitle("Membership Trends Over Time")
                                .setBasicChart(lineSpec))
                        .setPosition(new EmbeddedObjectPosition()
                                .setOverlayPosition(new OverlayPosition()
                                        .setAnchorCell(new GridCoordinate()
                                                .setSheetId(chartsSheetId)
                                                .setRowIndex(5).setColumnIndex(6))
                                        .setWidthPixels(650).setHeightPixels(320)));

                chartRequests.add(new Request().setAddChart(new AddChartRequest().setChart(lineChart)));
            }

            client.spreadsheets().batchUpdate(spreadsheetId,
                    new BatchUpdateSpreadsheetRequest().setRequests(chartRequests)).execute();

        } catch (Exception e) {
            log.warn("Could not setup native Google Sheets charts: {}", e.getMessage());
        }
    }

    public synchronized void upsertMember(Long memberId) {
        if (!isConfigured()) {
            automationLogService.log(AutomationType.SHEET_SYNC, "Member #" + memberId,
                    AutomationStatus.SKIPPED, "Google Sheets disabled or not configured");
            return;
        }

        try {
            Member member = memberRepo.findById(memberId).orElse(null);
            if (member == null || member.isDeleted()) {
                return;
            }

            Sheets client = getSheetsClient();
            String spreadsheetId = automationProperties.getSheets().getSpreadsheetId();

            List<Object> rowData = mapMemberToRow(member);

            // Fetch Column A to locate row by Member ID
            ValueRange colA = client.spreadsheets().values()
                    .get(spreadsheetId, TAB_MEMBERS + "!A:A")
                    .execute();

            int targetRow = -1;
            List<List<Object>> values = colA.getValues();
            if (values != null) {
                String targetIdStr = String.valueOf(member.getId());
                for (int i = 1; i < values.size(); i++) {
                    List<Object> r = values.get(i);
                    if (!r.isEmpty() && targetIdStr.equals(String.valueOf(r.get(0)).trim())) {
                        targetRow = i + 1; // 1-based row index
                        break;
                    }
                }
            }

            if (targetRow > 0) {
                // Update existing row
                String range = TAB_MEMBERS + "!A" + targetRow + ":O" + targetRow;
                ValueRange updateBody = new ValueRange().setValues(Collections.singletonList(rowData));
                client.spreadsheets().values()
                        .update(spreadsheetId, range, updateBody)
                        .setValueInputOption("USER_ENTERED")
                        .execute();
            } else {
                // Append new row
                ValueRange appendBody = new ValueRange().setValues(Collections.singletonList(rowData));
                client.spreadsheets().values()
                        .append(spreadsheetId, TAB_MEMBERS + "!A:O", appendBody)
                        .setValueInputOption("USER_ENTERED")
                        .execute();
            }

            automationLogService.log(AutomationType.SHEET_SYNC, "Member #" + member.getId(),
                    AutomationStatus.SUCCESS, "Synced member " + (member.getUser() != null ? member.getUser().getName() : ""));
        } catch (Exception e) {
            log.error("Failed to upsert member {} to Google Sheets: {}", memberId, e.getMessage(), e);
            automationLogService.log(AutomationType.SHEET_SYNC, "Member #" + memberId,
                    AutomationStatus.FAILED, e.getMessage());
        }
    }

    public synchronized void appendPayment(Long paymentId) {
        if (!isConfigured()) {
            automationLogService.log(AutomationType.SHEET_SYNC, "Payment #" + paymentId,
                    AutomationStatus.SKIPPED, "Google Sheets disabled or not configured");
            return;
        }

        try {
            Payment payment = paymentRepo.findById(paymentId).orElse(null);
            if (payment == null) {
                return;
            }

            Sheets client = getSheetsClient();
            String spreadsheetId = automationProperties.getSheets().getSpreadsheetId();

            // Check if payment already exists in column A
            ValueRange colA = client.spreadsheets().values()
                    .get(spreadsheetId, TAB_PAYMENTS + "!A:A")
                    .execute();

            String pidStr = String.valueOf(payment.getId());
            if (colA.getValues() != null) {
                for (int i = 1; i < colA.getValues().size(); i++) {
                    List<Object> r = colA.getValues().get(i);
                    if (!r.isEmpty() && pidStr.equals(String.valueOf(r.get(0)).trim())) {
                        log.info("Payment #{} already synced to Google Sheets. Skipping duplicate.", paymentId);
                        return;
                    }
                }
            }

            List<Object> rowData = mapPaymentToRow(payment);
            ValueRange appendBody = new ValueRange().setValues(Collections.singletonList(rowData));
            client.spreadsheets().values()
                    .append(spreadsheetId, TAB_PAYMENTS + "!A:I", appendBody)
                    .setValueInputOption("USER_ENTERED")
                    .execute();

            automationLogService.log(AutomationType.SHEET_SYNC, "Payment #" + payment.getId(),
                    AutomationStatus.SUCCESS, "Appended payment for " + (payment.getMember() != null && payment.getMember().getUser() != null ? payment.getMember().getUser().getName() : ""));
        } catch (Exception e) {
            log.error("Failed to append payment {} to Google Sheets: {}", paymentId, e.getMessage(), e);
            automationLogService.log(AutomationType.SHEET_SYNC, "Payment #" + paymentId,
                    AutomationStatus.FAILED, e.getMessage());
        }
    }

    public synchronized void appendDailyReport(Map<String, Object> stats) {
        if (!isConfigured()) {
            automationLogService.log(AutomationType.SHEET_SYNC, "Daily Report",
                    AutomationStatus.SKIPPED, "Google Sheets disabled or not configured");
            return;
        }

        try {
            Sheets client = getSheetsClient();
            String spreadsheetId = automationProperties.getSheets().getSpreadsheetId();

            String todayStr = LocalDate.now(KOLKATA_ZONE).format(DATE_FORMAT);

            List<Object> rowData = Arrays.asList(
                    todayStr,
                    stats.getOrDefault("totalMembers", 0),
                    stats.getOrDefault("activeMembers", 0),
                    stats.getOrDefault("expiredMembers", 0),
                    stats.getOrDefault("pendingMembers", 0),
                    stats.getOrDefault("newToday", 0),
                    String.format("%.2f", Double.parseDouble(String.valueOf(stats.getOrDefault("revenueToday", 0.0)))),
                    String.format("%.2f", Double.parseDouble(String.valueOf(stats.getOrDefault("revenueMonth", 0.0))))
            );

            // Check if today already has a row in Column A
            ValueRange colA = client.spreadsheets().values()
                    .get(spreadsheetId, TAB_DAILY_REPORT + "!A:A")
                    .execute();

            int targetRow = -1;
            if (colA.getValues() != null) {
                for (int i = 1; i < colA.getValues().size(); i++) {
                    List<Object> r = colA.getValues().get(i);
                    if (!r.isEmpty() && todayStr.equals(String.valueOf(r.get(0)).trim())) {
                        targetRow = i + 1;
                        break;
                    }
                }
            }

            if (targetRow > 0) {
                String range = TAB_DAILY_REPORT + "!A" + targetRow + ":H" + targetRow;
                ValueRange updateBody = new ValueRange().setValues(Collections.singletonList(rowData));
                client.spreadsheets().values()
                        .update(spreadsheetId, range, updateBody)
                        .setValueInputOption("USER_ENTERED")
                        .execute();
            } else {
                ValueRange appendBody = new ValueRange().setValues(Collections.singletonList(rowData));
                client.spreadsheets().values()
                        .append(spreadsheetId, TAB_DAILY_REPORT + "!A:H", appendBody)
                        .setValueInputOption("USER_ENTERED")
                        .execute();
            }

            automationLogService.log(AutomationType.SHEET_SYNC, "Daily Report " + todayStr,
                    AutomationStatus.SUCCESS, "Daily report synced successfully");
        } catch (Exception e) {
            log.error("Failed to append daily report to Google Sheets: {}", e.getMessage(), e);
            automationLogService.log(AutomationType.SHEET_SYNC, "Daily Report",
                    AutomationStatus.FAILED, e.getMessage());
        }
    }

    public synchronized void fullResync() {
        if (!isConfigured()) {
            automationLogService.log(AutomationType.SHEET_SYNC, "Full Resync",
                    AutomationStatus.SKIPPED, "Google Sheets disabled or not configured");
            return;
        }

        try {
            List<Member> members = memberRepo.findByDeletedFalse();
            members.sort(Comparator.comparing(Member::getId));

            Sheets client = getSheetsClient();
            String spreadsheetId = automationProperties.getSheets().getSpreadsheetId();

            // Clear existing rows 2+
            try {
                client.spreadsheets().values()
                        .clear(spreadsheetId, TAB_MEMBERS + "!A2:O", new ClearValuesRequest())
                        .execute();
            } catch (Exception clearEx) {
                log.warn("Notice clearing rows 2+: {}", clearEx.getMessage());
            }

            if (!members.isEmpty()) {
                List<List<Object>> allRows = new ArrayList<>();
                for (Member m : members) {
                    allRows.add(mapMemberToRow(m));
                }

                ValueRange body = new ValueRange().setValues(allRows);
                String range = TAB_MEMBERS + "!A2:O" + (allRows.size() + 1);
                client.spreadsheets().values()
                        .update(spreadsheetId, range, body)
                        .setValueInputOption("USER_ENTERED")
                        .execute();
            }

            automationLogService.log(AutomationType.SHEET_SYNC, "Full Resync (" + members.size() + " members)",
                    AutomationStatus.SUCCESS, "Full member sheet resync completed successfully");
        } catch (Exception e) {
            log.error("Failed full Google Sheets member resync: {}", e.getMessage(), e);
            automationLogService.log(AutomationType.SHEET_SYNC, "Full Resync",
                    AutomationStatus.FAILED, e.getMessage());
        }
    }

    @Scheduled(cron = "${automation.sheets.resync-cron:0 0 23 * * *}", zone = "Asia/Kolkata")
    public void scheduledResync() {
        fullResync();
    }

    private List<Object> mapMemberToRow(Member member) {
        String memberId = String.valueOf(member.getId());
        String name = member.getUser() != null ? member.getUser().getName() : "";
        String email = member.getUser() != null ? member.getUser().getEmail() : "";
        String phone = member.getUser() != null ? member.getUser().getPhone() : "";
        String gender = member.getGender() != null ? member.getGender() : "";
        String planName = member.getPlan() != null ? member.getPlan().getName() : "";
        String trainerName = (member.getTrainer() != null && member.getTrainer().getUser() != null)
                ? member.getTrainer().getUser().getName() : "";

        String joinDate = member.getJoinDate() != null ? member.getJoinDate().format(DATE_FORMAT) : "";
        String expiryDate = member.getExpiryDate() != null ? member.getExpiryDate().format(DATE_FORMAT) : "";

        String daysLeft = "";
        if (member.getExpiryDate() != null) {
            long diff = ChronoUnit.DAYS.between(LocalDate.now(), member.getExpiryDate());
            daysLeft = String.valueOf(Math.max(0, diff));
        }

        String status = member.getStatus() != null ? member.getStatus() : "";

        // Calculate total paid and last payment date from DB
        List<Payment> payments = paymentRepo.findByMemberId(member.getId());
        double totalPaid = 0.0;
        LocalDate lastPayDate = null;
        for (Payment p : payments) {
            if ("PAID".equalsIgnoreCase(p.getStatus())) {
                totalPaid += p.getAmount();
                if (p.getPaymentDate() != null) {
                    if (lastPayDate == null || p.getPaymentDate().isAfter(lastPayDate)) {
                        lastPayDate = p.getPaymentDate();
                    }
                }
            }
        }

        String totalPaidStr = String.format("%.2f", totalPaid);
        String lastPayDateStr = lastPayDate != null ? lastPayDate.format(DATE_FORMAT) : "";

        String registeredOn = "";
        if (member.getUser() != null && member.getUser().getCreatedAt() != null) {
            registeredOn = member.getUser().getCreatedAt().format(DATE_TIME_FORMAT);
        } else if (member.getJoinDate() != null) {
            registeredOn = member.getJoinDate().format(DATE_FORMAT);
        }

        String lastUpdated = LocalDateTime.now(KOLKATA_ZONE).format(DATE_TIME_FORMAT);

        return Arrays.asList(
                memberId, name, email, phone, gender, planName, trainerName,
                joinDate, expiryDate, daysLeft, status, totalPaidStr,
                lastPayDateStr, registeredOn, lastUpdated
        );
    }

    private List<Object> mapPaymentToRow(Payment payment) {
        String paymentId = String.valueOf(payment.getId());
        String memberId = payment.getMember() != null ? String.valueOf(payment.getMember().getId()) : "";
        String memberName = (payment.getMember() != null && payment.getMember().getUser() != null)
                ? payment.getMember().getUser().getName() : "";
        String planName = (payment.getMember() != null && payment.getMember().getPlan() != null)
                ? payment.getMember().getPlan().getName() : "";
        String amount = String.format("%.2f", payment.getAmount());
        String mode = payment.getMode() != null ? payment.getMode() : "";
        String status = payment.getStatus() != null ? payment.getStatus() : "";
        String paymentDate = payment.getPaymentDate() != null ? payment.getPaymentDate().format(DATE_FORMAT) : "";
        String invoiceNo = String.format("INV-%06d", payment.getId());

        return Arrays.asList(paymentId, memberId, memberName, planName, amount, mode, status, paymentDate, invoiceNo);
    }
}
