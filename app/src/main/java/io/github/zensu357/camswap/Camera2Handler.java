package io.github.zensu357.camswap;

import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.os.Handler;
import android.view.Surface;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executor;

import io.github.zensu357.camswap.api101.Api101Runtime;
import io.github.zensu357.camswap.utils.VideoManager;
import io.github.zensu357.camswap.utils.LogUtil;

/**
 * Camera2 Hook 处理器 —— 已原生化为 API 101 interceptor chain 风格。
 * <p>
 * 不再依赖 {@code api101.compat.*} 兼容层，直接使用
 * {@code Api101Runtime.requireModule().hook(method).intercept(chain -> ...)}。
 */
public class Camera2Handler implements ICameraHandler {

    @Override
    public void init(final Api101PackageContext packageContext) {
        final ClassLoader classLoader = packageContext.classLoader;
        final String packageName = packageContext.packageName;
        hookOpenCamera3Arg(classLoader, packageName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            hookOpenCameraExecutor(classLoader, packageName);
        }
        hookAddTarget(classLoader, packageName);
        hookRemoveTarget(classLoader, packageName);
        hookBuild(classLoader, packageName);

        // Pre-install session hooks on CameraDeviceImpl (framework internal class).
        // This ensures createCaptureSession interception works even if
        // the onOpened hook on obfuscated StateCallback classes doesn't fire
        // (due to ART optimization preventing hook on app-level classes).
        try {
            Class<?> deviceImplClass = Class.forName(
                    "android.hardware.camera2.impl.CameraDeviceImpl", false, classLoader);
            HookMain.camera2Hook.hookAllCreateSessionVariants(deviceImplClass);
            LogUtil.log("【CS】已在 CameraDeviceImpl 上预装 session hooks");
        } catch (Throwable t) {
            LogUtil.log("【CS】预装 session hooks 失败: " + t);
        }
    }

    // ================================================================
    // 1. CameraManager.openCamera(String, StateCallback, Handler)
    //    before-only: 记录 state callback 类 + 触发初始化
    // ================================================================
    private void hookOpenCamera3Arg(ClassLoader classLoader, String packageName) {
        try {
            Method method = resolveMethod(classLoader,
                    "android.hardware.camera2.CameraManager",
                    "openCamera", String.class, CameraDevice.StateCallback.class, Handler.class);
            Api101Runtime.requireModule().hook(method).intercept(chain -> {
                Object[] args = toArgs(chain.getArgs());
                try {
                    if (args[1] != null) {
                        File file = HookGuards.resolveVideoFile(true);
                        if (!HookGuards.shouldBypass(packageName, file)) {
                            // Always reset isFirstHookBuild on every openCamera call
                            HookMain.camera2Hook.isFirstHookBuild = true;
                            if (HookMain.c2_state_cb == null) {
                                // First StateCallback: capture and hook
                                HookMain.c2_state_cb = (CameraDevice.StateCallback) args[1];
                                HookMain.c2_state_callback = args[1].getClass();
                                LogUtil.log("【CS】1位参数初始化相机，类：" + HookMain.c2_state_callback.toString());
                                HookMain.process_camera2_init(HookMain.c2_state_cb);
                            } else if (!args[1].equals(HookMain.c2_state_cb)) {
                                // Different StateCallback instance: skip to avoid hijacking other cameras
                                LogUtil.log("【CS】检测到新的 StateCallback 实例，跳过重载");
                            } else {
                                // Same callback instance: camera reopened
                                LogUtil.log("【CS】1位参数重新打开相机（同一 StateCallback），重置播放状态");
                            }
                        }
                    }
                } catch (Throwable t) {
                    LogUtil.log("【CS】openCamera(3-arg) before 异常: " + t);
                }
                return chain.proceed(args);
            });
        } catch (Throwable t) {
            LogUtil.log("【CS】Hook openCamera(3-arg) 失败: " + t);
        }
    }
                        }
                    }
                } catch (Throwable t) {
                    LogUtil.log("【CS】openCamera(3-arg) before 异常: " + t);
                }
                return chain.proceed(args);
            });
        } catch (Throwable t) {
            LogUtil.log("【CS】Hook openCamera(3-arg) 失败: " + t);
        }
    }

    // ================================================================
    // 2. CameraManager.openCamera(String, Executor, StateCallback)
    //    after-only: 记录 state callback 类 (API 28+)
    // ================================================================
    private void hookOpenCameraExecutor(ClassLoader classLoader, String packageName) {
        try {
            Method method = resolveMethod(classLoader,
                    "android.hardware.camera2.CameraManager",
                    "openCamera", String.class, Executor.class, CameraDevice.StateCallback.class);
            Api101Runtime.requireModule().hook(method).intercept(chain -> {
                Object[] args = toArgs(chain.getArgs());
                Object result = chain.proceed(args);
                try {
                    if (args[2] != null) {
                        File file = HookGuards.resolveVideoFile(true);
                        if (!HookGuards.shouldBypass(packageName, file)) {
                            // Always reset isFirstHookBuild on every openCamera call.
                            HookMain.camera2Hook.isFirstHookBuild = true;
                            if (HookMain.c2_state_cb == null) {
                                HookMain.c2_state_cb = (CameraDevice.StateCallback) args[2];
                                HookMain.c2_state_callback = args[2].getClass();
                                LogUtil.log("【CS】2位参数初始化相机，类：" + HookMain.c2_state_callback.toString());
                                HookMain.process_camera2_init(HookMain.c2_state_cb);
                            } else if (!args[2].equals(HookMain.c2_state_cb)) {
                                LogUtil.log("【CS】检测到新的 StateCallback 实例（2位参数），跳过重载");
                            } else {
                                LogUtil.log("【CS】2位参数重新打开相机（同一 StateCallback），重置播放状态");
                            }
                        }
                    }
                } catch (Throwable t) {
                    LogUtil.log("【CS】openCamera(executor) after 异常: " + t);
                }
                return result;
            });
        } catch (Throwable t) {
            LogUtil.log("【CS】Hook openCamera(executor) 失败: " + t);
        }
    }
                        }
                    }
                } catch (Throwable t) {
                    LogUtil.log("【CS】openCamera(executor) after 异常: " + t);
                }
                return result;
            });
        } catch (Throwable t) {
            LogUtil.log("【CS】Hook openCamera(executor) 失败: " + t);
        }
    }

    // ================================================================
    // 3. CaptureRequest.Builder.addTarget(Surface)
    //    before-only: surface 替换逻辑
    // ================================================================
    private void hookAddTarget(ClassLoader classLoader, String packageName) {
        try {
            Method method = resolveMethod(classLoader,
                    "android.hardware.camera2.CaptureRequest$Builder",
                    "addTarget", Surface.class);
            Api101Runtime.requireModule().hook(method).intercept(chain -> {
                Object[] args = toArgs(chain.getArgs());
                try {
                    if (args[0] == null || chain.getThisObject() == null) {
                        return chain.proceed(args);
                    }
                    if (((Surface) args[0]).equals(HookMain.camera2Hook.getVirtualSurface())) {
                        return chain.proceed(args);
                    }
                    if (HookGuards.shouldBypass(packageName, HookGuards.getCurrentVideoFile())) {
                        return chain.proceed(args);
                    }
                    if (HookMain.camera2Hook.isCurrentSessionBypassed()) {
                        LogUtil.log("【CS】当前会话已旁路，保留原始目标: " + args[0]);
                        return chain.proceed(args);
                    }
                    // Only intercept builders belonging to our target CameraDevice
                    Object builder = chain.getThisObject();
                    android.hardware.camera2.CameraDevice device = HookMain.camera2Hook.getBuilderCameraDevice(builder);
                    if (device != null && !HookMain.camera2Hook.isOurCameraDevice(device)) {
                        LogUtil.log("【CS】跳过 addTarget: 非目标 CameraDevice");
                        return chain.proceed(args);
                    }

                    // Dynamic defense for Photo Fake
                    if (VideoManager.getConfig().getBoolean(ConfigManager.KEY_ENABLE_PHOTO_FAKE, false)
                            && HookMain.camera2Hook.isTrackedReaderSurface((Surface) args[0])) {
                        LogUtil.log("【CS】检测到 ImageReader Surface 在 addTarget: " + args[0]);
                        if (HookMain.camera2Hook.isJpegReaderSurface((Surface) args[0])) {
                            HookMain.camera2Hook.markPendingJpegCapture((Surface) args[0]);
                            LogUtil.log("【CS】保留 JPEG ImageReader 目标用于拍照: " + args[0]);
                            return chain.proceed(args);
                        }
                    }

                    Surface originalSurface = (Surface) args[0];
                    if (HookMain.camera2Hook.shouldKeepYuvReaderSurfaceForCurrentPackage(originalSurface)) {
                        LogUtil.log("【CS】YUV ImageReader 目标保留原始输出: "
                                + packageName + " -> " + originalSurface);
                        return chain.proceed(args);
                    }
                    if (HookMain.camera2Hook.isTrackedReaderSurface(originalSurface)) {
                        HookMain.camera2Hook.rememberReaderPlaybackSurface(originalSurface);
                        if (HookMain.camera2Hook.shouldKeepRealReaderSurfaceForCurrentPackage(originalSurface)) {
                            LogUtil.log("【CS】保留兼容性 ImageReader 目标: " + originalSurface);
                            return chain.proceed(args);
                        }
                    } else {
                        HookMain.camera2Hook.rememberPreviewSurface(originalSurface);
                    }
                    LogUtil.log("【CS】添加目标：" + originalSurface.toString());
                    // Ensure virtual surface exists (lazy creation if onOpened hook didn't fire)
                    Surface vSurface = HookMain.camera2Hook.getVirtualSurface();
                    if (vSurface == null || !vSurface.isValid()) {
                        vSurface = HookMain.camera2Hook.ensureVirtualSurface();
                    }
                    args[0] = vSurface;
                } catch (Throwable t) {
                    LogUtil.log("【CS】addTarget before 异常: " + t);
                }
                return chain.proceed(args);
            });
        } catch (Throwable t) {
            LogUtil.log("【CS】Hook addTarget 失败: " + t);
        }
    }
                    if (((Surface) args[0]).equals(HookMain.camera2Hook.getVirtualSurface())) {
                        return chain.proceed(args);
                    }
                    if (HookGuards.shouldBypass(packageName, HookGuards.getCurrentVideoFile())) {
                        return chain.proceed(args);
                    }
                    if (HookMain.camera2Hook.isCurrentSessionBypassed()) {
                        LogUtil.log("【CS】当前会话已旁路，保留原始目标: " + args[0]);
                        return chain.proceed(args);
                    }

                    // Dynamic defense for Photo Fake
                    if (VideoManager.getConfig().getBoolean(ConfigManager.KEY_ENABLE_PHOTO_FAKE, false)
                            && HookMain.camera2Hook.isTrackedReaderSurface((Surface) args[0])) {
                        LogUtil.log("【CS】检测到 ImageReader Surface 在 addTarget: " + args[0]);
                        if (HookMain.camera2Hook.isJpegReaderSurface((Surface) args[0])) {
                            HookMain.camera2Hook.markPendingJpegCapture((Surface) args[0]);
                            LogUtil.log("【CS】保留 JPEG ImageReader 目标用于拍照: " + args[0]);
                            return chain.proceed(args);
                        }
                    }

                    Surface originalSurface = (Surface) args[0];
                    if (HookMain.camera2Hook.shouldKeepYuvReaderSurfaceForCurrentPackage(originalSurface)) {
                        LogUtil.log("【CS】YUV ImageReader 目标保留原始输出: "
                                + packageName + " -> " + originalSurface);
                        return chain.proceed(args);
                    }
                    if (HookMain.camera2Hook.isTrackedReaderSurface(originalSurface)) {
                        HookMain.camera2Hook.rememberReaderPlaybackSurface(originalSurface);
                        if (HookMain.camera2Hook.shouldKeepRealReaderSurfaceForCurrentPackage(originalSurface)) {
                            LogUtil.log("【CS】保留兼容性 ImageReader 目标: " + originalSurface);
                            return chain.proceed(args);
                        }
                    } else {
                        HookMain.camera2Hook.rememberPreviewSurface(originalSurface);
                    }
                    LogUtil.log("【CS】添加目标：" + originalSurface.toString());
                    // Ensure virtual surface exists (lazy creation if onOpened hook didn't fire)
                    Surface vSurface = HookMain.camera2Hook.getVirtualSurface();
                    if (vSurface == null || !vSurface.isValid()) {
                        vSurface = HookMain.camera2Hook.ensureVirtualSurface();
                    }
                    args[0] = vSurface;
                } catch (Throwable t) {
                    LogUtil.log("【CS】addTarget before 异常: " + t);
                }
                return chain.proceed(args);
            });
        } catch (Throwable t) {
            LogUtil.log("【CS】Hook addTarget 失败: " + t);
        }
    }

    // ================================================================
    // 4. CaptureRequest.Builder.removeTarget(Surface)
    //    before-only: 清理 surface 引用
    // ================================================================
    private void hookRemoveTarget(ClassLoader classLoader, String packageName) {
        try {
            Method method = resolveMethod(classLoader,
                    "android.hardware.camera2.CaptureRequest$Builder",
                    "removeTarget", Surface.class);
            Api101Runtime.requireModule().hook(method).intercept(chain -> {
                Object[] args = toArgs(chain.getArgs());
                try {
                    Object builder = chain.getThisObject();
                    if (args[0] != null && builder != null
                            && !HookGuards.shouldBypass(packageName, HookGuards.getCurrentVideoFile())
                            && !HookMain.camera2Hook.isCurrentSessionBypassed()) {
                        // Only handle removals from builders belonging to our CameraDevice
                        android.hardware.camera2.CameraDevice device = HookMain.camera2Hook.getBuilderCameraDevice(builder);
                        if (device != null && HookMain.camera2Hook.isOurCameraDevice(device)) {
                            HookMain.camera2Hook.onTargetRemoved((Surface) args[0]);
                        }
                    }
                } catch (Throwable t) {
                    LogUtil.log("【CS】removeTarget before 异常: " + t);
                }
                return chain.proceed(args);
            });
        } catch (Throwable t) {
            LogUtil.log("【CS】Hook removeTarget 失败: " + t);
        }
    }

    // ================================================================
    // 5. CaptureRequest.Builder.build()
    //    before-only: 触发播放 (仅对我们的 CameraDevice)
    // ================================================================
    private void hookBuild(ClassLoader classLoader, String packageName) {
        try {
            Method method = resolveMethod(classLoader,
                    "android.hardware.camera2.CaptureRequest$Builder",
                    "build");
            Api101Runtime.requireModule().hook(method).intercept(chain -> {
                try {
                    Object builder = chain.getThisObject();
                    boolean isNewBuilder = builder != null
                            && !builder.equals(HookMain.camera2Hook.captureBuilder);
                    boolean hasPending = HookMain.camera2Hook.pendingPlayback;
                    boolean isFirstBuild = HookMain.camera2Hook.isFirstHookBuild;

                    // Determine which CameraDevice this builder belongs to
                    android.hardware.camera2.CameraDevice device = HookMain.camera2Hook.getBuilderCameraDevice(builder);
                    // Only proceed if builder's CameraDevice matches our tracked device
                    if (device == null || !HookMain.camera2Hook.isOurCameraDevice(device)) {
                        return chain.proceed(toArgs(chain.getArgs()));
                    }

                    if (builder != null && (isNewBuilder || hasPending || isFirstBuild)) {
                        HookMain.camera2Hook.captureBuilder = (CaptureRequest.Builder) builder;
                        if (!HookGuards.shouldBypass(packageName, HookGuards.getCurrentVideoFile())) {
                            if (HookMain.camera2Hook.isCurrentSessionBypassed()) {
                                LogUtil.log("【CS】当前会话已旁路，跳过虚拟播放启动");
                            } else {
                                LogUtil.log("【CS】开始build请求"
                                        + (hasPending ? " (延迟重试)" : "")
                                        + (isFirstBuild ? " (首次build)" : ""));
                                if (VideoManager.getConfig().getBoolean(ConfigManager.KEY_ENABLE_PHOTO_FAKE, false)
                                        && HookMain.camera2Hook.pendingPhotoSurface != null
                                        && HookMain.camera2Hook.isJpegReaderSurface(
                                                HookMain.camera2Hook.pendingPhotoSurface)) {
                                    LogUtil.log("【CS】build 已标记等待 JPEG acquire 替换: "
                                            + HookMain.camera2Hook.pendingPhotoSurface);
                                }
                                HookMain.process_camera2_play();
                            }
                        }
                    }
                } catch (Throwable t) {
                    LogUtil.log("【CS】build before 异常: " + t);
                }
                return chain.proceed(toArgs(chain.getArgs()));
            });
        } catch (Throwable t) {
            LogUtil.log("【CS】Hook build 失败: " + t);
        }
    }

    // ================================================================
    // Utilities
    // ================================================================

    private static Method resolveMethod(ClassLoader classLoader, String className,
            String methodName, Class<?>... parameterTypes) throws Exception {
        Class<?> clazz = Class.forName(className, false, classLoader);
        Class<?> current = clazz;
        while (current != null) {
            try {
                Method m = current.getDeclaredMethod(methodName, parameterTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(className + "#" + methodName);
    }

    private static Object[] toArgs(List<Object> args) {
        return args.toArray(new Object[0]);
    }
}
