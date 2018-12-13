/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.oeffi.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.pte.NetworkId;

import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import androidx.core.app.ActivityManagerCompat;
import androidx.core.content.FileProvider;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public class ErrorReporter implements Thread.UncaughtExceptionHandler {
    private static final String STACKTRACE_FILENAME = ".stacktrace";

    private Thread.UncaughtExceptionHandler previousHandler;
    private File stackTraceFile;
    private final StringBuilder report = new StringBuilder();
    private File filesDir, cacheDir;
    private NetworkId networkId;

    private static final Logger log = LoggerFactory.getLogger(ErrorReporter.class);

    private static ErrorReporter instance;

    public static ErrorReporter getInstance() {
        if (instance == null)
            instance = new ErrorReporter();
        return instance;
    }

    public void setNetworkId(final NetworkId networkId) {
        this.networkId = networkId;
    }

    public void init(final Context context) {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);

        filesDir = context.getFilesDir();
        cacheDir = context.getCacheDir();

        stackTraceFile = new File(cacheDir, STACKTRACE_FILENAME);

        report.append("=== collected at launch time ===\n\n");
        report.append("Network: " + (networkId != null ? networkId.name() : "-") + "\n\n");
        appendReport(report, context);
    }

    private static void appendReport(final StringBuilder report, final Context context) {
        try {
            final PackageManager pm = context.getPackageManager();
            final PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);

            report.append("Date: " + new Date() + "\n");
            report.append("Version: " + pi.versionName + " (" + pi.versionCode + ")\n");
            report.append("Package: " + pi.packageName + "\n");
            final String installerPackageName = Installer.installerPackageName(context);
            final Installer installer = Installer.from(installerPackageName);
            if (installer != null)
                report.append("Installer: " + installer.displayName + " (" + installerPackageName + ")\n");
            else
                report.append("Installer: unknown\n");
        } catch (final NameNotFoundException x) {
            x.printStackTrace();
        }

        try {
            appendDeviceInfo(report, context);
        } catch (final IOException x) {
            report.append("Exception while adding device info: ").append(x.getMessage()).append('\n');
        }

        report.append("\n\n\n");
    }

    private long getAvailableInternalMemorySize() {
        final File path = Environment.getDataDirectory();
        final StatFs stat = new StatFs(path.getPath());
        final long blockSize = stat.getBlockSize();
        final long availableBlocks = stat.getAvailableBlocks();
        return availableBlocks * blockSize;
    }

    private long getTotalInternalMemorySize() {
        final File path = Environment.getDataDirectory();
        final StatFs stat = new StatFs(path.getPath());
        final long blockSize = stat.getBlockSize();
        final long totalBlocks = stat.getBlockCount();
        return totalBlocks * blockSize;
    }

    public void uncaughtException(final Thread t, final Throwable exception) {
        log.warn("crashing because of uncaught exception", exception);
        try {
            report.append("=== collected at exception time ===\n\n");

            report.append("Network: " + (networkId != null ? networkId.name() : "-") + "\n\n");
            report.append("Total Internal memory: " + getTotalInternalMemorySize() + "\n");
            report.append("Available Internal memory: " + getAvailableInternalMemorySize() + "\n");
            report.append("\n");

            final Writer result = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(result);
            exception.printStackTrace(printWriter);
            final String stacktrace = result.toString();
            report.append(stacktrace + "\n");
            printWriter.close();

            // append contents of directories
            report.append("\nContents of FilesDir " + filesDir + ":\n");
            appendReport(report, filesDir, 0);
            report.append("\nContents of CacheDir " + cacheDir + ":\n");
            appendReport(report, cacheDir, 0);

            saveAsFile(report.toString());
        } catch (final Exception x) {
            x.printStackTrace();
        }

        previousHandler.uncaughtException(t, exception);
    }

    public static void appendDeviceInfo(final Appendable report, final Context context) throws IOException {
        final Resources res = context.getResources();
        final Configuration config = res.getConfiguration();
        final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        report.append("Manufacturer: " + Build.MANUFACTURER + "\n");
        report.append("Phone Model: " + Build.MODEL + "\n");
        report.append("Android Version: " + Build.VERSION.RELEASE + "\n");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            report.append("Android security patch level: ").append(Build.VERSION.SECURITY_PATCH).append("\n");
        report.append("ABIs: ").append(Joiner.on(", ").skipNulls().join(Strings.emptyToNull(Build.CPU_ABI),
                Strings.emptyToNull(Build.CPU_ABI2))).append("\n");
        report.append("Board: " + Build.BOARD + "\n");
        report.append("Brand: " + Build.BRAND + "\n");
        report.append("Device: " + Build.DEVICE + "\n");
        report.append("Model: " + Build.MODEL + "\n");
        report.append("Product: " + Build.PRODUCT + "\n");
        report.append("Configuration: " + config + "\n");
        report.append("Screen Layout:" //
                + " size " + (config.screenLayout & android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) //
                + " long " + (config.screenLayout & android.content.res.Configuration.SCREENLAYOUT_LONG_MASK) //
                + " layoutdir " + (config.screenLayout & android.content.res.Configuration.SCREENLAYOUT_LAYOUTDIR_MASK) //
                + " round " + (config.screenLayout & android.content.res.Configuration.SCREENLAYOUT_ROUND_MASK) + "\n");
        report.append("Display Metrics: " + res.getDisplayMetrics() + "\n");
        report.append("Memory Class: " + activityManager.getMemoryClass() + "/" + activityManager.getLargeMemoryClass()
                + (ActivityManagerCompat.isLowRamDevice(activityManager) ? " (low RAM device)" : "") + "\n");
        report.append("Runtime: ").append(System.getProperty("java.vm.name")).append(" ")
                .append(System.getProperty("java.vm.version")).append("\n");
    }

    private static void sendErrorMail(final Context context, final String errorContent) {
        final Matcher m = Pattern.compile("Version: (.+?) ").matcher(errorContent);
        final String versionName = m.find() ? m.group(1) : "";
        final String subject = context.getString(R.string.error_reporter_crash_mail_subject) + " " + versionName;
        send(context, subject, errorContent);
    }

    public static void sendBugMail(final Context context) {
        final StringBuilder report = new StringBuilder(context.getString(R.string.error_reporter_questions));
        report.append("\n=== collected at reporting time ===\n\n");
        appendReport(report, context);

        // append contents of directories
        report.append("\nContents of FilesDir " + context.getFilesDir() + ":\n");
        appendReport(report, context.getFilesDir(), 0);

        report.append("\n\n");
        report.append(context.getString(R.string.error_reporter_footer));

        final String subject = context.getString(R.string.error_reporter_bug_mail_subject);
        send(context, subject, report);
    }

    private static void send(final Context context, final String subject, final CharSequence report) {
        final File logDir = new File(context.getFilesDir(), "log");
        final ArrayList<Uri> attachments = new ArrayList<>();

        if (logDir.exists())
            for (final File logFile : logDir.listFiles())
                if (logFile.isFile() && logFile.length() > 0)
                    attachments.add(FileProvider.getUriForFile(context, context.getPackageName(), logFile));

        final Intent intent;
        if (attachments.size() == 0) {
            intent = new Intent(Intent.ACTION_SEND);
            intent.setType("message/rfc822");
        } else {
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType("text/plain");
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, attachments);
        }

        intent.putExtra(Intent.EXTRA_EMAIL, new String[] { Constants.REPORT_EMAIL });
        intent.putExtra(Intent.EXTRA_TEXT, report.toString());
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.error_reporter_mail_intent_chooser_title)));
    }

    private void saveAsFile(final String errorContent) {
        try {
            final FileOutputStream trace = new FileOutputStream(stackTraceFile);
            trace.write(errorContent.getBytes());
            trace.close();
        } catch (final IOException x) {
            // swallow
        }
    }

    private void sendError(final Context context) {
        try {
            final StringBuilder errorText = new StringBuilder();

            final BufferedReader input = new BufferedReader(new FileReader(stackTraceFile));
            String line;
            while ((line = input.readLine()) != null)
                errorText.append(line + "\n");
            input.close();

            errorText.append("\n\n");
            errorText.append(context.getString(R.string.error_reporter_footer));

            sendErrorMail(context, errorText.toString());
        } catch (Exception x) {
            x.printStackTrace();
        }
    }

    private static void appendReport(final StringBuilder report, final File file, final int indent) {
        for (int i = 0; i < indent; i++)
            report.append("  - ");

        final Formatter formatter = new Formatter(report);
        formatter.format(Locale.US, "%tF %tT  %s  [%d]\n", file.lastModified(), file.lastModified(), file.getName(),
                file.length());
        formatter.close();

        if (file.isDirectory())
            for (final File f : file.listFiles())
                appendReport(report, f, indent + 1);
    }

    private final static Pattern PATTERN_VERSION = Pattern.compile("<dt id=\"(\\d+)\">([^<]*)</dt>");

    public void check(final Context context, final int applicationVersionCode, final String applicationVersionFlavor) {
        if (!stackTraceFile.exists())
            return;

        final HttpUrl.Builder url = HttpUrl.parse(context.getString(R.string.about_changelog_summary)).newBuilder();
        url.addQueryParameter("version", Integer.toString(applicationVersionCode));
        if (applicationVersionFlavor != null)
            url.addQueryParameter("flavor", applicationVersionFlavor);
        url.addQueryParameter("sdk", Integer.toString(Build.VERSION.SDK_INT));
        url.addQueryParameter("check", null);
        final Request.Builder request = new Request.Builder().url(url.build());
        final Call call = Application.OKHTTP_CLIENT.newCall(request.build());
        final Handler callbackHandler = new Handler(Looper.myLooper());
        call.enqueue(new Callback() {
            public void onResponse(final Call call, final Response response) throws IOException {
                try {
                    final CharSequence page = response.body().string();
                    final Matcher m = PATTERN_VERSION.matcher(page);
                    if (m.find()) {
                        final int versionCode = Integer.parseInt(m.group(1));
                        final String versionName = m.group(2);
                        log.info("According to {}, the current version is {} ({})", url, versionName, versionCode);
                        if (versionCode > applicationVersionCode)
                            callback(versionName);
                        else
                            callback(null);
                    }
                } finally {
                    response.close();
                }
            }

            public void onFailure(final Call call, final IOException x) {
                callback(null);
            }

            private void callback(final String newVersion) {
                callbackHandler.post(new Runnable() {
                    public void run() {
                        dialog(context, newVersion);
                    }
                });
            }
        });
    }

    private void dialog(final Context context, final String newVersion) {
        final DialogBuilder builder = DialogBuilder.warn(context, R.string.alert_crash_report_title);
        builder.setMessage(newVersion != null ? context.getString(R.string.alert_crash_report_new_version, newVersion)
                : context.getString(R.string.alert_crash_report_message));
        builder.setNegativeButton(R.string.alert_crash_report_negative, new OnClickListener() {
            public void onClick(final DialogInterface dialog, final int which) {
                stackTraceFile.delete();
            }
        });
        if (newVersion != null) {
            builder.setNeutralButton(R.string.alert_crash_report_update, new OnClickListener() {
                public void onClick(final DialogInterface dialog, final int which) {
                    stackTraceFile.delete();
                    context.startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=" + context.getPackageName())));
                }
            });
            builder.setPositiveButton(R.string.alert_crash_report_download, new OnClickListener() {
                public void onClick(final DialogInterface dialog, final int which) {
                    stackTraceFile.delete();
                    context.startActivity(
                            new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.OEFFI_BASE_URL + "download.html")));
                }
            });
        } else {
            builder.setPositiveButton(R.string.alert_crash_report_positive, new OnClickListener() {
                public void onClick(final DialogInterface dialog, final int which) {
                    sendError(context);
                    stackTraceFile.delete();
                }
            });
        }
        builder.setOnCancelListener(new OnCancelListener() {
            public void onCancel(final DialogInterface dialog) {
                stackTraceFile.delete();
            }
        });

        try {
            builder.show();
        } catch (final Exception x) {
            log.warn("Problem showing crash report dialog", x);
        }
    }
}
