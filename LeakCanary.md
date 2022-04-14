# LeakCanary

### 1. 如何查看进程内存

```java
// 进入adb shell 
// 查看dalvik虚拟机相关内存信息
getprop|grep dalvik
```
![image.png](https://upload-images.jianshu.io/upload_images/5872452-bacea81f6327f907.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
  	1. [dalvik.vm.heapstartsize]: [16m] :  进程的起始内存大小
  	1. [dalvik.vm.heapsize]: [512m] : manifest中设置了largeHeap=true 之后，可以使用的最大内存值 
  	2. [dalvik.vm.heapgrowthlimit]: [192m] : 普通应用的内存限制大小

### LeakCanary监测
  	1. 监控生命周期，对象是否应该被回收，却没有被回收
  	2. 通过可达性分析，查看对象是否内存泄漏
  	3. 获取内存泄漏对象的引用链

#### 监测回调
	1. 通过Application.registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks())来监测Activity的生命周期
	2. 在生命周期中监测Activity的创建和销毁
	3. 在Activity中通过FragmentManager的回调FragmentLifecycleCallbacks()来监测Fragment的创建和销毁
```java
  public RefWatcher buildAndInstall() {
    if (LeakCanaryInternals.installedRefWatcher != null) {
      throw new UnsupportedOperationException("buildAndInstall() should only be called once.");
    }
    RefWatcher refWatcher = build();
    if (refWatcher != DISABLED) {
      if (watchActivities) {
        ActivityRefWatcher.install(context, refWatcher);
      }
      if (watchFragments) {
        FragmentRefWatcher.Helper.install(context, refWatcher);
      }
    }
    LeakCanaryInternals.installedRefWatcher = refWatcher;
    return refWatcher;
  }

public final class ActivityRefWatcher {

  public static void installOnIcsPlus(Application application, RefWatcher refWatcher) {
    install(application, refWatcher);
  }

  public static void install(Context context, RefWatcher refWatcher) {
    Application application = (Application) context.getApplicationContext();
    ActivityRefWatcher activityRefWatcher = new ActivityRefWatcher(application, refWatcher);

    application.registerActivityLifecycleCallbacks(activityRefWatcher.lifecycleCallbacks);
  }

  private final Application.ActivityLifecycleCallbacks lifecycleCallbacks =
      new ActivityLifecycleCallbacksAdapter() {
        @Override public void onActivityDestroyed(Activity activity) {
          refWatcher.watch(activity);
        }
      };

  private final Application application;
  private final RefWatcher refWatcher;

  private ActivityRefWatcher(Application application, RefWatcher refWatcher) {
    this.application = application;
    this.refWatcher = refWatcher;
  }

  public void watchActivities() {
    // Make sure you don't get installed twice.
    stopWatchingActivities();
    application.registerActivityLifecycleCallbacks(lifecycleCallbacks);
  }

  public void stopWatchingActivities() {
    application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks);
  }
}


/**
 * Internal class used to watch for fragments leaks.
 */
public interface FragmentRefWatcher {

  void watchFragments(Activity activity);

  final class Helper {

    private static final String SUPPORT_FRAGMENT_REF_WATCHER_CLASS_NAME =
        "com.squareup.leakcanary.internal.SupportFragmentRefWatcher";

    public static void install(Context context, RefWatcher refWatcher) {
      List<FragmentRefWatcher> fragmentRefWatchers = new ArrayList<>();

      if (SDK_INT >= O) {
        fragmentRefWatchers.add(new AndroidOFragmentRefWatcher(refWatcher));
      }

      try {
        Class<?> fragmentRefWatcherClass = Class.forName(SUPPORT_FRAGMENT_REF_WATCHER_CLASS_NAME);
        Constructor<?> constructor =
            fragmentRefWatcherClass.getDeclaredConstructor(RefWatcher.class);
        FragmentRefWatcher supportFragmentRefWatcher =
            (FragmentRefWatcher) constructor.newInstance(refWatcher);
        fragmentRefWatchers.add(supportFragmentRefWatcher);
      } catch (Exception ignored) {
      }

      if (fragmentRefWatchers.size() == 0) {
        return;
      }

      Helper helper = new Helper(fragmentRefWatchers);

      Application application = (Application) context.getApplicationContext();
      application.registerActivityLifecycleCallbacks(helper.activityLifecycleCallbacks);
    }

    private final Application.ActivityLifecycleCallbacks activityLifecycleCallbacks =
        new ActivityLifecycleCallbacksAdapter() {
          @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            for (FragmentRefWatcher watcher : fragmentRefWatchers) {
              watcher.watchFragments(activity);
            }
          }
        };

    private final List<FragmentRefWatcher> fragmentRefWatchers;

    private Helper(List<FragmentRefWatcher> fragmentRefWatchers) {
      this.fragmentRefWatchers = fragmentRefWatchers;
    }
  }
}
```
#### 弱引用+队列
	1. 通过弱引用来引用Activity或者Fragment
	2. 如果需要被销毁的Activity在GC之后，还能通过弱引用拿到对象。说明Activity内存泄漏
	3. 