package com.dev2.offlineauthentication.uitls;

import android.os.Build;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * Reflection tools.
 */
public class Ref {

    private static Object sVmRuntime;
    private static Method setHiddenApiExemptions;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                Method forName = Class.class.getDeclaredMethod("forName", String.class);
                Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);

                Class<?> vmRuntimeClass = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
                Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null);
                setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", new Class[]{String[].class});
                sVmRuntime = getRuntime.invoke(null);
            } catch (InvocationTargetException e) {
                Log.v("Ref", "reflect bootstrap failed:", e.getCause());
            } catch (Throwable e) {
                Log.v("Ref", "reflect bootstrap failed:", e);
            }
        }
    }

    /**
     * make the method exempted from hidden API check.
     *
     * @param method the method signature prefix.
     * @return true if success.
     */
    public static boolean exempt(String method) {
        return exempt(new String[]{method});
    }

    /**
     * make specific methods exempted from hidden API check.
     *
     * @param methods the method signature prefix, such as "Ldalvik/system", "Landroid" or even "L"
     * @return true if success
     */
    public static boolean exempt(String... methods) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (sVmRuntime == null || setHiddenApiExemptions == null) {
                return false;
            }
            try {
                setHiddenApiExemptions.invoke(sVmRuntime, new Object[]{methods});
                return true;
            } catch (Throwable e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Make all hidden API exempted.
     *
     * @return true if success.
     */
    public static boolean exemptAll() {
        return exempt(new String[]{"L"});
    }

    public static boolean unseal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // Below Android P, ignore
            return true;
        }
        return exemptAll();
    }

    public static <T> T newInstance(String className, Class[] classTypes, Object[] classArgs) throws ClassNotFoundException, SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Class clazz = Class.forName(className);
        Constructor constructor = clazz.getDeclaredConstructor(classTypes);
        constructor.setAccessible(true);
        return (T) constructor.newInstance(classArgs);
    }

    public static <T> T newInstance(String className) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        Class clazz = Class.forName(className);
        return (T) clazz.newInstance();
    }

    public static <T> T invokeMethod(Object instance, String methodName,
                                     Class[] methodTypes, Object[] methodArgs) throws NoSuchMethodException,
            IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        Method accessMethod = getMethod(instance.getClass(), methodName, methodTypes);
        accessMethod.setAccessible(true);
        return (T) accessMethod.invoke(instance, methodArgs);
    }

    public static <T> T invokeMethod(Object instance, String methodName, Object... methodArgs) throws
            NoSuchMethodException,
            IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        Method accessMethod = getMethod(instance.getClass(), methodName);
        accessMethod.setAccessible(true);
        return (T) accessMethod.invoke(instance, methodArgs);
    }

    public static <T> T invokeStaticMethod(Class clazz, String methodName,
                                           Class[] methodTypes, Object[] methodArgs) throws NoSuchMethodException,
            IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        Method accessMethod = getMethod(clazz, methodName, methodTypes);
        accessMethod.setAccessible(true);
        return (T) accessMethod.invoke(null, methodArgs);
    }

    public static <T> T invokeStaticMethod(Class clazz, String methodName, Object... methodArgs) throws
            NoSuchMethodException,
            IllegalArgumentException, InvocationTargetException, IllegalAccessException {
        Method accessMethod = getMethod(clazz, methodName);
        accessMethod.setAccessible(true);
        return (T) accessMethod.invoke(null, methodArgs);
    }

    public static void setStaticValue(Class cls, String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field field = getField(cls, fieldName);
        field.setAccessible(true);
        if (Modifier.isFinal(field.getModifiers())) {
            try {
                getField(Field.class, "accessFlags");
                setValue(field, "accessFlags", field.getModifiers() & ~Modifier.FINAL);
            } catch (NoSuchFieldException e) {
                setValue(getValue(field, "artField"), "accessFlags", field.getModifiers() & ~Modifier.FINAL);
            }
        }
        field.set(null, value);
    }

    public static void setValue(Object instance, String fieldName, Object value) throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field field = getField(instance.getClass(), fieldName);
        field.setAccessible(true);
        if (Modifier.isFinal(field.getModifiers())) {
            try {
                getField(Field.class, "accessFlags");
                setValue(field, "accessFlags", field.getModifiers() & ~Modifier.FINAL);
            } catch (NoSuchFieldException e) {
                setValue(getValue(field, "artField"), "accessFlags", field.getModifiers() & ~Modifier.FINAL);
            }
        }
        field.set(instance, value);
    }

    public static <T> T getValue(Object instance, String fieldName) throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field field = getField(instance.getClass(), fieldName);
        field.setAccessible(true);
        return (T) field.get(instance);
    }

    public static <T> T getStaticValue(Class clazz, String fieldName) throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field field = getField(clazz, fieldName);
        field.setAccessible(true);
        return (T) field.get(null);
    }

    private static Method getMethod(Class clazz, String methodName,
                                    Class[] classTypes) throws NoSuchMethodException {

        if (clazz == null) {
            throw new NoSuchMethodException("No such method : " + methodName + " with artTypes:" + Arrays.toString
                    (classTypes));
        }

        try {
            Method accessMethod = clazz.getDeclaredMethod(methodName, classTypes);
            accessMethod.setAccessible(true);
            return accessMethod;
        } catch (NoSuchMethodException e) {
            return getMethod(clazz.getSuperclass(), methodName, classTypes);
        }
    }

    private static Method getMethod(Class clazz, String methodName) throws NoSuchMethodException {

        if (clazz == null) {
            throw new NoSuchMethodException("No such method : " + methodName);
        }

        try {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (method.getName().equals(methodName)) {
                    method.setAccessible(true);
                    return method;
                }
            }
            return getMethod(clazz.getSuperclass(), methodName);
        } catch (NoSuchMethodException e) {
            return getMethod(clazz.getSuperclass(), methodName);
        }
    }

    private static Field getField(Class clazz, String fieldName) throws NoSuchFieldException {

        if (clazz == null) {
            throw new NoSuchFieldException("No such field : " + fieldName);
        }

        try {
            Field accessField = clazz.getDeclaredField(fieldName);
            accessField.setAccessible(true);
            return accessField;
        } catch (NoSuchFieldException e) {
            return getField(clazz.getSuperclass(), fieldName);
        }
    }

}
