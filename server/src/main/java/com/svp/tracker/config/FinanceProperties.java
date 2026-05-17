package com.svp.tracker.config;

import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JDBC access to Oracle Robinhood table. {@link #transactionDateColumn()} + {@link #transactionDateQuoted()} control
 * period filters; {@link #transactionDateOracleFormatMask()} controls string-date parsing when set.
 */
@ConfigurationProperties(prefix = "tracker.finance")
public record FinanceProperties(
        /** Table name or SCHEMA.TABLE */
        String robinhoodTable,
        /** Safety cap for FETCH FIRST */
        int maxTransactionRows,
        /**
         * Oracle column used for finance period filters (e.g. {@code activity_date}). Empty = year/month query params
         * rejected; unfiltered fetch still allowed.
         */
        String transactionDateColumn,
        /**
         * When true, {@link #transactionDateColumn()} is emitted as a quoted identifier ({@code "name"}) so case is
         * preserved (e.g. {@code t."activity_date"}). When false, the name is normalized to an unquoted uppercase
         * identifier ({@code ACTIVITY_DATE}).
         */
        boolean transactionDateQuoted,
        /**
         * When non-blank, period filters and ORDER BY wrap the date column with
         * {@code TO_TIMESTAMP(TRIM(t.&lt;col&gt;), '&lt;mask&gt;')} so {@code EXTRACT} works on VARCHAR2/CHAR. Empty when the
         * column is already DATE or TIMESTAMP (e.g. {@code YYYY-MM-DD} or {@code MM/DD/YYYY HH24:MI:SS}).
         */
        String transactionDateOracleFormatMask,
        /**
         * Column for individual-stock filter and {@code /symbols} list (e.g. {@code instrument}, {@code symbol}).
         * Empty disables those APIs.
         */
        String stockSymbolColumn,
        /**
         * When true, {@link #stockSymbolColumn()} is emitted as a quoted identifier (case preserved).
         */
        boolean stockSymbolColumnQuoted,
        /** Safety cap for {@code SELECT DISTINCT} symbol list. */
        int maxSymbolListRows,
        /**
         * Optional Oracle scalar expression (use table alias {@code t}) for the stock column in {@code /symbols} and
         * symbol filter, instead of {@code TRIM(t.&lt;column&gt;)}. Example for LOB: {@code DBMS_LOB.SUBSTR(t.INSTRUMENT,4000,1)}.
         * Empty uses {@code TRIM}.
         */
        String stockSymbolColumnOracleExpr,
        /** Row cap when loading transactions for {@code /stocks-summary} (can exceed max-transaction-rows). */
        int maxStocksSummaryRows,
        /** Max CSV upload size for local Robinhood import endpoint. */
        int maxImportCsvBytes,
        /** Max preview rows returned by CSV import endpoint. */
        int importPreviewRows,
        /** Max parse error messages returned by CSV import endpoint. */
        int maxImportErrors,
        /**
         * When true, CSV import skips rows that match an earlier row in the same upload or an existing row in
         * {@link #robinhoodTable()} (all nine columns compared with null-safe equality).
         */
        boolean importDeduplicate,
        /**
         * Absolute path to a folder of Robinhood CSV files for {@code POST /import-csv-directory}. Empty disables that
         * endpoint until configured.
         */
        String robinhoodCsvImportDirectory,
        /**
         * After a successful {@code apply=true} directory import, each fully successful file is moved here. Empty
         * disables moving (and directory import will reject when apply=true).
         */
        String robinhoodCsvUploadedDirectory,
        /** Max number of {@code *.csv} files processed in one directory import request. */
        int maxDirectoryImportFiles,
        /**
         * Flat rate (0–1) applied to positive realized gains for Robinhood estimated-tax projection in reports (e.g.
         * {@code 0.22} = 22%). Not tax advice.
         */
        double robinhoodEstimatedTaxRate,
        /**
         * Optional JupyterLab URL for Reports → Robinhood (e.g. {@code http://127.0.0.1:8888/lab}). Empty hides the
         * link in the UI.
         */
        String robinhoodJupyterLabUrl,
        /** When true, call robinhood-notebook-svc to render parameterized notebooks to HTML. */
        boolean robinhoodNotebookServiceEnabled,
        /** Base URL for robinhood-notebook-svc (e.g. {@code http://robinhood-notebook:8010}). */
        String robinhoodNotebookServiceBaseUrl,
        /** HTTP timeout for notebook render requests (ms). */
        int robinhoodNotebookServiceTimeoutMs,
        /**
         * Optional path to Robinhood portfolio snapshot JSON (mobile/web app figures). When set, used for Reports →
         * Robinhood portfolio overview for the snapshot's calendar year. Classpath {@code robinhood-portfolio-snapshot.json}
         * and {@code config/robinhood-portfolio-snapshot.json} are fallbacks.
         */
        String robinhoodPortfolioSnapshotPath,
        /** Optional cash / buying power for computed portfolio value (when not using snapshot). */
        String robinhoodCashBalance,
        /** Enable/disable internet-backed stock news endpoint. */
        boolean newsEnabled,
        /** Max items returned by stock news endpoint. */
        int newsMaxItems,
        /** HTTP timeout for stock news fetch. */
        int newsTimeoutMs,
        /**
         * When true, the server calls Alpha Vantage for stock quotes (scheduled refresh and on-demand cache fill).
         * When false, no outbound Alpha Vantage HTTP requests are made.
         */
        boolean alphaVantageEnabled,
        /** Alpha Vantage API key used for quote retrieval in alerts/crawler/swing sections. */
        String alphaVantageApiKey,
        /** Alpha Vantage query endpoint. */
        String alphaVantageBaseUrl) {

    private static final Pattern SAFE_ORACLE_DATE_FORMAT =
            Pattern.compile("^[A-Za-z0-9\\-:/. ,]+$");

    public FinanceProperties {
        if (robinhoodTable == null || robinhoodTable.isBlank()) {
            robinhoodTable = "SPULIC.ROBINHOOD_TRANSACTIONS";
        }
        if (maxTransactionRows < 1) {
            maxTransactionRows = 10_000;
        }
        if (maxTransactionRows > 500_000) {
            maxTransactionRows = 500_000;
        }
        if (transactionDateColumn == null) {
            transactionDateColumn = "";
        } else {
            transactionDateColumn = transactionDateColumn.trim();
        }
        if (transactionDateOracleFormatMask == null) {
            transactionDateOracleFormatMask = "";
        } else {
            transactionDateOracleFormatMask = transactionDateOracleFormatMask.trim();
            if (transactionDateOracleFormatMask.contains("'")) {
                throw new IllegalArgumentException(
                        "tracker.finance.transaction-date-oracle-format-mask must not contain single quotes");
            }
            if (!transactionDateOracleFormatMask.isEmpty()
                    && !SAFE_ORACLE_DATE_FORMAT.matcher(transactionDateOracleFormatMask).matches()) {
                throw new IllegalArgumentException(
                        "tracker.finance.transaction-date-oracle-format-mask has invalid characters: "
                                + transactionDateOracleFormatMask);
            }
            if (transactionDateOracleFormatMask.length() > 128) {
                throw new IllegalArgumentException(
                        "tracker.finance.transaction-date-oracle-format-mask exceeds 128 characters");
            }
        }
        if (stockSymbolColumn == null) {
            stockSymbolColumn = "";
        } else {
            stockSymbolColumn = stockSymbolColumn.trim();
        }
        if (maxSymbolListRows < 1) {
            maxSymbolListRows = 5_000;
        }
        if (maxSymbolListRows > 50_000) {
            maxSymbolListRows = 50_000;
        }
        if (stockSymbolColumnOracleExpr == null) {
            stockSymbolColumnOracleExpr = "";
        } else {
            stockSymbolColumnOracleExpr = stockSymbolColumnOracleExpr.trim();
        }
        if (maxStocksSummaryRows < 1) {
            maxStocksSummaryRows = 200_000;
        }
        if (maxStocksSummaryRows > 500_000) {
            maxStocksSummaryRows = 500_000;
        }
        if (maxImportCsvBytes < 1_024) {
            maxImportCsvBytes = 5_000_000;
        }
        if (maxImportCsvBytes > 50_000_000) {
            maxImportCsvBytes = 50_000_000;
        }
        if (importPreviewRows < 1) {
            importPreviewRows = 25;
        }
        if (importPreviewRows > 500) {
            importPreviewRows = 500;
        }
        if (maxImportErrors < 1) {
            maxImportErrors = 25;
        }
        if (maxImportErrors > 1_000) {
            maxImportErrors = 1_000;
        }
        if (robinhoodCsvImportDirectory == null) {
            robinhoodCsvImportDirectory = "";
        } else {
            robinhoodCsvImportDirectory = robinhoodCsvImportDirectory.trim();
        }
        if (robinhoodCsvUploadedDirectory == null) {
            robinhoodCsvUploadedDirectory = "";
        } else {
            robinhoodCsvUploadedDirectory = robinhoodCsvUploadedDirectory.trim();
        }
        if (maxDirectoryImportFiles < 1) {
            maxDirectoryImportFiles = 500;
        }
        if (maxDirectoryImportFiles > 10_000) {
            maxDirectoryImportFiles = 10_000;
        }
        if (robinhoodEstimatedTaxRate < 0) {
            robinhoodEstimatedTaxRate = 0;
        } else if (robinhoodEstimatedTaxRate > 1) {
            robinhoodEstimatedTaxRate = 1;
        }
        if (robinhoodJupyterLabUrl == null) {
            robinhoodJupyterLabUrl = "";
        } else {
            robinhoodJupyterLabUrl = robinhoodJupyterLabUrl.trim();
        }
        if (robinhoodNotebookServiceBaseUrl == null) {
            robinhoodNotebookServiceBaseUrl = "";
        } else {
            robinhoodNotebookServiceBaseUrl = robinhoodNotebookServiceBaseUrl.trim();
        }
        if (robinhoodNotebookServiceTimeoutMs < 1_000) {
            robinhoodNotebookServiceTimeoutMs = 120_000;
        }
        if (robinhoodNotebookServiceTimeoutMs > 600_000) {
            robinhoodNotebookServiceTimeoutMs = 600_000;
        }
        if (robinhoodPortfolioSnapshotPath == null) {
            robinhoodPortfolioSnapshotPath = "";
        } else {
            robinhoodPortfolioSnapshotPath = robinhoodPortfolioSnapshotPath.trim();
        }
        if (robinhoodCashBalance == null) {
            robinhoodCashBalance = "";
        } else {
            robinhoodCashBalance = robinhoodCashBalance.trim();
        }
        if (newsMaxItems < 1) {
            newsMaxItems = 10;
        }
        if (newsMaxItems > 50) {
            newsMaxItems = 50;
        }
        if (newsTimeoutMs < 1_000) {
            newsTimeoutMs = 8_000;
        }
        if (newsTimeoutMs > 60_000) {
            newsTimeoutMs = 60_000;
        }
        if (alphaVantageApiKey == null) {
            alphaVantageApiKey = "";
        } else {
            alphaVantageApiKey = alphaVantageApiKey.trim();
        }
        if (alphaVantageBaseUrl == null || alphaVantageBaseUrl.isBlank()) {
            alphaVantageBaseUrl = "https://www.alphavantage.co/query";
        } else {
            alphaVantageBaseUrl = alphaVantageBaseUrl.trim();
        }
    }
}
