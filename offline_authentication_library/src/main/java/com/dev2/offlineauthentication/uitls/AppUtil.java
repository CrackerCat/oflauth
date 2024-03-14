package com.dev2.offlineauthentication.uitls;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;

import java.lang.reflect.InvocationTargetException;

@SuppressLint("PrivateApi")
public class AppUtil {
    private static final Class<?> ACTIVITY_THREAD_CLS;

    static {
        try {
            ACTIVITY_THREAD_CLS = Class.forName("android.app.ActivityThread");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static Object sActivityThread;

    public static Object getActivityThread() {
        if (sActivityThread == null) {
            try {
                sActivityThread = Ref.invokeStaticMethod(ACTIVITY_THREAD_CLS, "currentActivityThread");
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return sActivityThread;
    }

    public static boolean hasPermission(Context ctx, String perm) {
        int grant = ctx.getPackageManager().checkPermission(perm, ctx.getPackageName());
        return grant == PackageManager.PERMISSION_GRANTED;
    }

    public static Application getApplication() {
        Application app = null;
        try {
            app = (Application) Ref.invokeStaticMethod(ACTIVITY_THREAD_CLS, "currentApplication");
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (app == null) {
            try {
                app = (Application) Ref.invokeMethod(getActivityThread(), "getApplication");
            } catch (Throwable e1) {
                e1.printStackTrace();
            }
        }
        return app;
    }

    public static String getPackageName() {
        try {
            return (String) Ref.invokeStaticMethod(ACTIVITY_THREAD_CLS, "currentPackageName");
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }
}
