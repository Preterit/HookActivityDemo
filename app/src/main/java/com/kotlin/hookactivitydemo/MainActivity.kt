package com.kotlin.hookactivitydemo

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    val EXTRA_TARGET_INTENT = "extra_target_intent"

    private lateinit var btnHook: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnHook = findViewById(R.id.btn_hook)

        btnHook.setOnClickListener { hookActivity() }
    }

    private fun hookActivity() {
        Log.e(TAG, "hookActivity: " + Build.VERSION.SDK_INT)

        hookIActivityManager()
        this.hookHandler()
        startActivity(Intent(this, TargerActivity::class.java))
    }

    private fun hookHandler() {
        try {
            val atClass = Class.forName("android.app.ActivityThread")
            /***** 获取ActivityThread对象 *****/
            val sCurrentActivityThreadField = atClass.getDeclaredField("sCurrentActivityThread")
            /***** 获取sCurrentActivityThread变量 *****/
            sCurrentActivityThreadField.isAccessible = true
            /***** 设置sCurrentActivityThread变量可用 *****/
            val sCurrentActivityThread = sCurrentActivityThreadField.get(null)
            /***** 获取ActivityThread对象 *****/

            //ActivityThread 一个app进程 只有一个，获取它的mH
            val mHField = atClass.getDeclaredField("mH")
            /***** 获取mH变量 *****/
            mHField.isAccessible = true
            val mH: Handler = mHField.get(sCurrentActivityThread) as Handler
            /***** 获取Handler对象 *****/


            //获取mCallBack
            val mCallbackField = Handler::class.java.getDeclaredField("mCallback")
            mCallbackField.isAccessible = true
            mCallbackField.set(mH, Handler.Callback { msg ->
                Log.e(TAG, "handleMessage: " + msg.what)
                when (msg.what) {
                    100 -> {
                        // hook 回复
                        val intentField = msg.obj.javaClass.getDeclaredField("intent")
                        intentField.isAccessible = true
                        val intent = intentField.get(msg.obj) as Intent
                        val targetIntent = intent.getParcelableExtra<Intent>(EXTRA_TARGET_INTENT)
                        intent.component = targetIntent.component
                    }
                    159 -> {
                        val obj = msg.obj
                        Log.e(TAG, "handleMessage: obj $obj")
                        val mActivityCallbacksField =
                            obj.javaClass.getDeclaredField("mActivityCallbacks")
                        mActivityCallbacksField.isAccessible = true
                        val mActivityCallbacks = mActivityCallbacksField.get(obj) as List<*>
                        Log.e(TAG, "handleMessage: mActivityCallbacks=$ mActivityCallbacks")
                        if (mActivityCallbacks.isNotEmpty()) {
                            Log.i(TAG, "handleMessage: size= " + mActivityCallbacks.size)
                            val className = "android.app.servertransaction.LaunchActivityItem"
                            if (mActivityCallbacks[0]?.javaClass?.canonicalName.equals(className)) {
                                val obj = mActivityCallbacks[0];
                                val intentField = obj?.javaClass?.getDeclaredField("mIntent")
                                intentField?.isAccessible = true
                                val intent = intentField?.get(obj) as Intent
                                val targetIntent =
                                    intent.getParcelableExtra<Intent>(EXTRA_TARGET_INTENT)
                                intent.component = targetIntent.component
                            }
                        }
                    }
                }
                mH.handleMessage(msg)
                true
            })


        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }


    private fun test() {
    }

    private fun hookIActivityManager() {
        try {
            var gDefaultField: Field? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  // android O 之前源码 就有改动
                val activityManager = Class.forName("android.app.ActivityManager")
                gDefaultField = activityManager.getDeclaredField("IActivityManagerSingleton")
            }

            gDefaultField?.isAccessible = true
            //Singlon<IActivityManager>
            val gDefault = gDefaultField?.get(null)

            //拿到Singleton的Class对象
            val singletonClass = Class.forName("android.util.Singleton")
            val mInstanceField = singletonClass.getDeclaredField("mInstance")
            mInstanceField.isAccessible = true

            //获取到ActivityManagerNative里面的gDefault对象里面的原始的IActivityManager对象
            val rawIActivityManager = mInstanceField.get(gDefault)

            //进行动态代理
            val classLoader: ClassLoader = Thread.currentThread().contextClassLoader
            val iActivityManagerInterFace = Class.forName("android.app.IActivityManager")

            //生成IActivityManager代理对象
            val proxy =
                Proxy.newProxyInstance(classLoader, arrayOf<Class<*>>(iActivityManagerInterFace),
                    InvocationHandler { proxy, method, args ->
                        Log.e(TAG, "invoke: method " + method.name)

                        if ("startActivity" == method.name) {
                            Log.e(TAG, "准备工作")
                            for (arg in args) {
                                Log.e(TAG, "arg : $arg")
                            }

                            // 替换  把Target换成我们的Stud,欺骗AMS的权限验证
                            // 拿到原始的Intent,然后保存
                            var raw: Intent? = null
                            var index = 0

                            for (i in args.indices) {
                                if (args[i] is Intent) {
                                    raw = args[i] as Intent?
                                    index = i
                                    break
                                }
                            }

                            Log.e(TAG, "invoke raw= $raw")
                            val newIntent = Intent()
                            val studPackage = "com.kotlin.hookactivitydemo"
                            newIntent.component =
                                ComponentName(studPackage, TestActivity::class.java.name)
                            // 把这个newIntent 放回到args,达到一个欺骗AMS的目的
                            newIntent.putExtra(EXTRA_TARGET_INTENT, raw)
                            args[index] = newIntent
                        }
                        method.invoke(rawIActivityManager, *args)
                    })
            //把我们的代理对象融入到framework
            mInstanceField.set(gDefault, proxy)

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }
}
