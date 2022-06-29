Zygot启动SystemServer：

	pid = Zygote.forkSystemServer
	//z0ygote 进程：pid = 子进程pid
	//子进程：pid = 0
	
	if(pid = 0){
		if(hasSecondZygote(abiList)){
			waitForSecondaryZygote(socketName);
		}
		zygoteServer.closeServerSocket();
		// SystemServer.main 方法。启动SystemServer
		return handleSystemServerProcess(parsedArgs);
	}
	
	return null;
	
	handleSystemServerProcess(parsedArgs);
		-> ZygoteInit.zygoteInit
			//开启Binder线程池
			->ZygoteInit.nativeZygoteInit();
				->AndroidRuntime->onZygoteInit(); 
			//运行SystemServer.main 
			->RuntimeInit.applicationInit();
				mMethod.invoke();//通过反射运行SystemServer.main
			
SystemServer进程：

//初始化SystemServer上下文
1. createSystemContext();
	->ActivityThread.systemMain();
		->ActivityThread.attach();
			->ContextImpl context = ContextImpl.createAppContext(this,getSystemContext().mPackageInfo);
			   mInitialApplication = context.mPackageInfo.makeApplication(true,null);//通过反射创建Application 
//创建SystemServiceManager
2. mSystemServiceManager = new SystemServiceManager(mSystemContext);
3. startBootstrapServices(t);//引导服务 -- AMS 
		android(10) 开始有ATMS:
		1. 创建 ATMS(ActivityTaskManagerService) -- 管理Activity
					mService = new ActivityTaskManagerService();
					publishBunderService(Context.ACTIVITY_TASK_SERVICE,mService);//注册Binder服务
					mService.start(); 
		2. 创建AMS(ActivityManagerService ) -- 
					mService = new ActivityManagerService();
						1. BroadcastConstants
						2. ActivityServices
						3. BatteryStatsService
						4. ProcessStatsService
						5. mActivityTaskManager.initialize
					mService.start();
4. startCoreServices(t);//核心服务

5. startOtherServices(t);//其他服务 --WMS
		mActivityManagerService.systemReady()//启动Launcher
			->ActivityStarter.execute(); 栈的管理相关
			-> startActivityHome();
		startSystemUI();//启动SystemUI


SystemServiceManager:管理服务的生命周期
ServiceManager：管理Binder服务的

resumeTopActivityInnerLocked
	-> mStackSupervisor.startSpecificActivity
			-> 进程存在 --  c
			-> 进程不存在--
				AMS创建Socket 通知 Zygote 创建进程
				ZygoteServer.runSelectLoop(abiList); 
				-->通过反射启动ActivityThread.main
					->thread.attach();
						->mgr.attachApplication(mAppThread,startSeq);//把应用句柄发送给AMS.这个句柄是ApplicationThread通信。ApplicationThread是一个Binder对象
							-> mStackSupervisor.startSpecificActivity
								-> ActivityThread.handleLaunchActivity
									-> performLaunchActivity();
										-> mInstrumentation.newActivity(); //反射创建Activity
										-> mInstrumentation.callActivityOnCreate();//调用onCreate方法
									
