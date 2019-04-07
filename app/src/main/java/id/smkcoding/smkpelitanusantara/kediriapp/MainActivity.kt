package id.smkcoding.smkpelitanusantara.kediriapp

import android.app.Activity
import android.app.AlertDialog
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.microsoft.windowsazure.mobileservices.MobileServiceClient
import com.microsoft.windowsazure.mobileservices.http.NextServiceFilterCallback
import com.microsoft.windowsazure.mobileservices.http.ServiceFilter
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterRequest
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterResponse
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable
import com.microsoft.windowsazure.mobileservices.table.query.QueryOperations.`val`
import com.microsoft.windowsazure.mobileservices.table.sync.localstore.ColumnDataType
import com.microsoft.windowsazure.mobileservices.table.sync.localstore.MobileServiceLocalStoreException
import com.microsoft.windowsazure.mobileservices.table.sync.localstore.SQLiteLocalStore
import com.microsoft.windowsazure.mobileservices.table.sync.synchandler.SimpleSyncHandler
import com.squareup.okhttp.OkHttpClient
import java.net.MalformedURLException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private var mClient: MobileServiceClient? = null
    private var mToDoTable: MobileServiceTable<TodoItem>? = null
    private var mAdapter: TodoItemAdapter? = null
    private var mTextNewToDo: EditText? = null
    private var mProgressBar: ProgressBar? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mProgressBar = findViewById<View>(R.id.loadingProgressBar) as ProgressBar
        mProgressBar!!.visibility = ProgressBar.GONE
        try {
            mClient = MobileServiceClient(
                "https://kediriapp.azurewebsites.net",
                this).withFilter(ProgressFilter())
            mClient!!.setAndroidHttpClientFactory {
                val client = OkHttpClient()
                client.setReadTimeout(20, TimeUnit.SECONDS)
                client.setWriteTimeout(20, TimeUnit.SECONDS)
                client
            }
            mToDoTable = mClient!!.getTable<TodoItem>(TodoItem::class.java!!)

            initLocalStore().get()

            mTextNewToDo = findViewById<View>(R.id.textNewToDo) as EditText

            mAdapter = TodoItemAdapter(this, R.layout.row_list_to_do)
            val listViewToDo = findViewById<View>(R.id.listViewToDo) as ListView
            listViewToDo.adapter = mAdapter

            refreshItemsFromTable()

        } catch (e: MalformedURLException) {
            createAndShowDialog(Exception("There was an error creating the Mobile Service. Verify the URL"), "Error")
        } catch (e: Exception) {
            createAndShowDialog(e, "Error")
        }
    }
    fun checkItem(item: TodoItem) {
        if (mClient == null) {
            return
        }
        item.isComplete = true

        val task = object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg params: Void): Void? {
                try {

                    checkItemInTable(item)
                    runOnUiThread {
                        if (item.isComplete) {
                            mAdapter!!.remove(item)
                        }
                    }
                } catch (e: Exception) {
                    createAndShowDialogFromTask(e, "Error")
                }

                return null
            }
        }

        runAsyncTask(task)
    }
    @Throws(ExecutionException::class, InterruptedException::class)
    fun checkItemInTable(item: TodoItem) {
        mToDoTable!!.update(item).get()
    }
    fun addItem(view: View) {
        if (mClient == null) {
            return
        }
        val item = TodoItem()

        item.text = mTextNewToDo!!.text.toString()
        item.isComplete = false

        // Insert the new item
        val task = object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg params: Void): Void? {
                try {
                    val entity = addItemInTable(item)

                    runOnUiThread {
                        if (!entity.isComplete) {
                            mAdapter!!.add(entity)
                        }
                    }
                } catch (e: Exception) {
                    createAndShowDialogFromTask(e, "Error")
                }

                return null
            }
        }

        runAsyncTask(task)

        mTextNewToDo!!.setText("")
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    fun addItemInTable(item: TodoItem): TodoItem {
        return mToDoTable!!.insert(item).get()
    }

    private fun refreshItemsFromTable() {

        val task = object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg params: Void): Void? {

                try {
                    val results = refreshItemsFromMobileServiceTable()
                    runOnUiThread {
                        mAdapter!!.clear()

                        for (item in results) {
                            mAdapter!!.add(item)
                        }
                    }
                } catch (e: Exception) {
                    createAndShowDialogFromTask(e, "Error")
                }

                return null
            }
        }

        runAsyncTask(task)
    }
    @Throws(ExecutionException::class, InterruptedException::class)
    private fun refreshItemsFromMobileServiceTable(): List<TodoItem> {
        return mToDoTable!!.where().field("complete").eq(`val`(false)).execute().get()
    }
    @Throws(MobileServiceLocalStoreException::class, ExecutionException::class, InterruptedException::class)
    private fun initLocalStore(): AsyncTask<Void, Void, Void> {

        val task = object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg params: Void): Void? {
                try {

                    val syncContext = mClient!!.syncContext

                    if (syncContext.isInitialized)
                        return null

                    val localStore = SQLiteLocalStore(mClient!!.context, "OfflineStore", null, 1)

                    val tableDefinition = HashMap<String, ColumnDataType>()
                    tableDefinition["id"] = ColumnDataType.String
                    tableDefinition["text"] = ColumnDataType.String
                    tableDefinition["complete"] = ColumnDataType.Boolean

                    localStore.defineTable("ToDoItem", tableDefinition)

                    val handler = SimpleSyncHandler()

                    syncContext.initialize(localStore, handler).get()

                } catch (e: Exception) {
                    createAndShowDialogFromTask(e, "Error")
                }

                return null
            }
        }

        return runAsyncTask(task)
    }
    private fun createAndShowDialogFromTask(exception: Exception, title: String) {
        runOnUiThread { createAndShowDialog(exception, title) }
    }

    private fun createAndShowDialog(exception: Exception, title: String) {
        var ex: Throwable = exception
        if (exception.cause != null) {
            ex = exception.cause!!
        }
        createAndShowDialog(ex.message.toString(), title)
    }
    private fun createAndShowDialog(message: String, title: String) {
        val builder = AlertDialog.Builder(this)

        builder.setMessage(message)
        builder.setTitle(title)
        builder.create().show()
    }
    private fun runAsyncTask(task: AsyncTask<Void, Void, Void>): AsyncTask<Void, Void, Void> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        } else {
            task.execute()
        }
    }
    private inner class ProgressFilter : ServiceFilter {

        override fun handleRequest(request: ServiceFilterRequest, nextServiceFilterCallback: NextServiceFilterCallback): ListenableFuture<ServiceFilterResponse> {

            val resultFuture = SettableFuture.create<ServiceFilterResponse>()


            runOnUiThread { if (mProgressBar != null) mProgressBar!!.visibility = ProgressBar.VISIBLE }

            val future = nextServiceFilterCallback.onNext(request)

            Futures.addCallback(future, object : FutureCallback<ServiceFilterResponse> {
                override fun onFailure(e: Throwable) {
                    resultFuture.setException(e)
                }

                override fun onSuccess(response: ServiceFilterResponse?) {
                    runOnUiThread { if (mProgressBar != null) mProgressBar!!.visibility = ProgressBar.GONE }

                    resultFuture.set(response)
                }
            })

            return resultFuture
        }
    }

}
