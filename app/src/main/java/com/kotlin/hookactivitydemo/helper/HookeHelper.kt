package com.kotlin.hookactivitydemo.helper

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.util.Log
import com.kotlin.hookactivitydemo.MainActivity
import java.lang.reflect.Field
import java.lang.reflect.Proxy

/**
 * @author :  lwb
 * Date: 2019/10/29
 * Desc:
 */
class HookeHelper {

    private val TAG:String = "HookeHelper"
    fun hookIActivityManager() {
        //TODO:
        //        1. 找到了Hook的点
        //        2. hook点 动态代理 静态？
        //        3. 获取到getDefault的IActivityManager原始对象
        //        4. 动态代理 准备classloader 接口
        //        5  classloader, 获取当前线程
        //        6. 接口 Class.forName("android.app.IActivityManager");
        //        7. Proxy.newProxyInstance() 得到一个IActivityManagerProxy
        //        8. IActivityManagerProxy融入到framework

        //            public abstract class Singleton<T> {
        //                private T mInstance;
        //
        //                protected abstract T create();
        //
        //                public final T get() {
        //                    synchronized (this) {
        //                        if (mInstance == null) {
        //                            mInstance = create();
        //                        }
        //                        return mInstance;
        //                    }
        //                }
        //            }

        try {
            var gDefaultField: Field? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val activityManager = Class.forName("android.app.ActivityManager")
                gDefaultField = activityManager.getDeclaredField("IActivityManagerSingleton")
            } else {
                val activityManager = Class.forName("android.app.ActivityManagerNative")
                //拿到 Singleton<IActivityManager> gDefault
                gDefaultField = activityManager.getDeclaredField("gDefault")
            }

            gDefaultField.isAccessible = true
            //Singlon<IActivityManager>
            val gDefault = gDefaultField.get(null)

            //拿到Singleton的Class对象
            val singletonClass = Class.forName("android.util.Singleton")
            val mInstanceField = singletonClass.getDeclaredField("mInstance")
            mInstanceField.isAccessible = true
            //获取到ActivityManagerNative里面的gDefault对象里面的原始的IActivityManager对象
            val rawIActivityManager = mInstanceField.get(gDefault)

            //进行动态代理
            val classLoader = Thread.currentThread().contextClassLoader
            val iActivityManagerInterface = Class.forName("android.app.IActivityManager")
            //生产IActivityManager的代理对象
            val proxy = Proxy.newProxyInstance(
                classLoader, arrayOf(iActivityManagerInterface)
            ) { proxy, method, args ->
                Log.i(TAG, "invoke: method " + method.name)
                if ("startActivity" == method.name) {
                    Log.i(TAG, "准备启动activity")
                    for (obj in args) {
                        Log.i(TAG, "invoke: obj= $obj")
                    }

                    //偷梁换柱 把Target 换成我们的Stub,欺骗AMS的权限验证
                    //拿到原始的Intent,然后保存
                    var raw: Intent? = null
                    var index = 0
                    for (i in args.indices) {
                        if (args[i] is Intent) {
                            raw = args[i] as Intent
                            index = i
                            break
                        }
                    }
                    Log.i(TAG, "invoke: raw= " + raw!!)

                    //替换成Stub
                    val newIntent = Intent()
                    val stubPackage = "com.zero.activityhookdemo"
                    newIntent.component = ComponentName(stubPackage, MainActivity::class.java.name)
                    //把这个newIntent放回到args,达到了一个欺骗AMS的目的
//                    newIntent.putExtra(EXTRA_TARGET_INTENT, raw)
                    args[index] = newIntent

                }
                method.invoke(rawIActivityManager, *args)
            }

            //把我们的代理对象融入到framework
            mInstanceField.set(gDefault, proxy)


        } catch (e: Exception) {
            Log.e(TAG, "hookIActivityManager: " + e.message)
            e.printStackTrace()
        }

    }


}