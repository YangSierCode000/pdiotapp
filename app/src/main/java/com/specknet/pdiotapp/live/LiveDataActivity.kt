package com.specknet.pdiotapp.live

//import org.apache.commons.math3.complex.Complex
//import org.apache.commons.math3.transform.DftNormalization
//import org.apache.commons.math3.transform.FastFourierTransformer
//import org.apache.commons.math3.transform.TransformType
//import org.apache.commons.math3.util.MathArrays
//import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetFileDescriptor
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import com.specknet.pdiotapp.utils.ThingyLiveData
import org.tensorflow.lite.Interpreter
import java.nio.*
import java.nio.channels.FileChannel
import com.specknet.pdiotapp.HistoryDB
import com.specknet.pdiotapp.HistorySingleton


class LiveDataActivity : AppCompatActivity() {

    // global graph variables
    lateinit var dataSet_res_accel_x: LineDataSet
    lateinit var dataSet_res_accel_y: LineDataSet
    lateinit var dataSet_res_accel_z: LineDataSet

    lateinit var dataSet_res_gyro_x: LineDataSet
    lateinit var dataSet_res_gyro_y: LineDataSet
    lateinit var dataSet_res_gyro_z: LineDataSet

    lateinit var dataSet_thingy_accel_x: LineDataSet
    lateinit var dataSet_thingy_accel_y: LineDataSet
    lateinit var dataSet_thingy_accel_z: LineDataSet

    lateinit var dataSet_thingy_gyro_x: LineDataSet
    lateinit var dataSet_thingy_gyro_y: LineDataSet
    lateinit var dataSet_thingy_gyro_z: LineDataSet

    var time = 0f
    lateinit var allRespeckData: LineData

    lateinit var allThingyData: LineData

    lateinit var respeckChart: LineChart
    lateinit var thingyChart: LineChart

    // global broadcast receiver so we can unregister it
    lateinit var respeckLiveUpdateReceiver: BroadcastReceiver
    lateinit var thingyLiveUpdateReceiver: BroadcastReceiver
    lateinit var looperRespeck: Looper
    lateinit var looperThingy: Looper

    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    val filterTestThingy = IntentFilter(Constants.ACTION_THINGY_BROADCAST)
    private lateinit var tflite_thingy: Interpreter
    private lateinit var tflite_res: Interpreter
    val activities = mapOf(
        0 to "ascending stairs normal",
        1 to "descending stairs normal",
        2 to "lying down on back coughing",
        3 to "lying down on back hyperventilating",
        4 to "lying down on back laughing",
        5 to "lying down on back normal",
        6 to "lying down on back singing",
        7 to "lying down on back talking",
        8 to "lying down on left coughing",
        9 to "lying down on left hyperventilating",
        10 to "lying down on left laughing",
        11 to "lying down on left normal",
        12 to "lying down on left singing",
        13 to "lying down on left talking",
        14 to "lying down on right coughing",
        15 to "lying down on right hyperventilating",
        16 to "lying down on right laughing",
        17 to "lying down on right normal",
        18 to "lying down on right singing",
        19 to "lying down on right talking",
        20 to "lying down on stomach coughing",
        21 to "lying down on stomach hyperventilating",
        22 to "lying down on stomach laughing",
        23 to "lying down on stomach normal",
        24 to "lying down on stomach singing",
        25 to "lying down on stomach talking",
        26 to "miscellaneous movements normal",
        27 to "normal walking normal",
        28 to "running normal",
        29 to "shuffle walking normal",
        30 to "sitting/standing coughing",
        31 to "sitting/standing eating",
        32 to "sitting/standing hyperventilating",
        33 to "sitting/standing laughing",
        34 to "sitting/standing normal",
        35 to "sitting/standing singing",
        36 to "sitting/standing talking"
    )

    val minVal = -300f
    val maxVal = 300f
    var pastActivity: String = ""
    private val historySingleton: HistorySingleton by lazy { HistorySingleton.getInstance(applicationContext) }
    private val historyDB: HistoryDB by lazy { historySingleton.historyDB }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_data)

        setupCharts()

        // set up the broadcast receiver
        respeckLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.d("Live", "onReceive: liveData = " + liveData)

                    // get all relevant intent contents
                    val x = liveData.accelX
                    val y = liveData.accelY
                    val z = liveData.accelZ

                    time += 1
                    updateGraph("respeck", x, y, z)

                    val gyro_x = liveData.gyro.x
                    val gyro_y = liveData.gyro.y
                    val gyro_z = liveData.gyro.z

                    dataSet_res_gyro_x.addEntry(Entry(time, gyro_x))
                    dataSet_res_gyro_y.addEntry(Entry(time, gyro_y))
                    dataSet_res_gyro_z.addEntry(Entry(time, gyro_z))

                    runModelWithResAvailableData()
                }
            }
        }

        // register receiver on another thread
        val handlerThreadRespeck = HandlerThread("bgThreadRespeckLive")
        handlerThreadRespeck.start()
        looperRespeck = handlerThreadRespeck.looper
        val handlerRespeck = Handler(looperRespeck)
        this.registerReceiver(respeckLiveUpdateReceiver, filterTestRespeck, null, handlerRespeck)

        // set up the broadcast receiver
        thingyLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_THINGY_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.THINGY_LIVE_DATA) as ThingyLiveData
                    Log.d("Live", "onReceive: liveData = " + liveData)

                    // get all relevant intent contents
                    val accl_x = liveData.accelX
                    val accl_y = liveData.accelY
                    val accl_z = liveData.accelZ

                    time += 1
                    updateGraph("thingy", accl_x, accl_y, accl_z)

                    val gyro_x = liveData.gyro.x
                    val gyro_y = liveData.gyro.y
                    val gyro_z = liveData.gyro.z

                    dataSet_thingy_gyro_x.addEntry(Entry(time, gyro_x))
                    dataSet_thingy_gyro_y.addEntry(Entry(time, gyro_y))
                    dataSet_thingy_gyro_z.addEntry(Entry(time, gyro_z))

//                    runModelWithThingyAvailableData() // TODO waiting thingy model
                }
            }
        }

        // register receiver on another thread
        val handlerThreadThingy = HandlerThread("bgThreadThingyLive")
        handlerThreadThingy.start()
        looperThingy = handlerThreadThingy.looper
        val handlerThingy = Handler(looperThingy)
        this.registerReceiver(thingyLiveUpdateReceiver, filterTestThingy, null, handlerThingy)

//        // Load Thingy TFLite model
//        val modelPath_thingy = "model_thingy_accl_gyro_no_norm_25.tflite" // TODO thingy model file name
//        val assetFileDescriptor_thingy: AssetFileDescriptor = assets.openFd(modelPath_thingy)
//        val fileInputStream_thingy = assetFileDescriptor_thingy.createInputStream()
//        val fileChannel_thingy: FileChannel = fileInputStream_thingy.channel
//        val startOffset_thingy: Long = assetFileDescriptor_thingy.startOffset
//        val declaredLength_thingy: Long = assetFileDescriptor_thingy.declaredLength
//        val tfliteModel_thingy: MappedByteBuffer = fileChannel_thingy.map(
//            FileChannel.MapMode.READ_ONLY,
//            startOffset_thingy,
//            declaredLength_thingy
//        )
//
//        val options_thingy = Interpreter.Options()
//        tflite_thingy = Interpreter(tfliteModel_thingy, options_thingy)

        //val modelPath_res = "model_respeck_accl_only_no_norm_task_1_50.tflite"
        val modelPath_res = "model_respeck_accl_gyro_norm_task_5_50.tflite"
        val assetFileDescriptor_res: AssetFileDescriptor = assets.openFd(modelPath_res)
        val fileInputStream_res = assetFileDescriptor_res.createInputStream()
        val fileChannel_res: FileChannel = fileInputStream_res.channel
        val startOffset_res: Long = assetFileDescriptor_res.startOffset
        val declaredLength_res: Long = assetFileDescriptor_res.declaredLength
        val tfliteModel_res: MappedByteBuffer = fileChannel_res.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset_res,
            declaredLength_res
        )

        val options_res = Interpreter.Options()
        tflite_res = Interpreter(tfliteModel_res, options_res)
    }

//    fun runModelWithThingyAvailableData() {
//        // Make sure you have enough data
//        if (dataSet_thingy_accel_x.entryCount < 25
//            || dataSet_thingy_accel_x.entryCount % 100 != 0
//        ) {
//            return
//        }
//
//        val entries_thingy_accel_x = dataSet_thingy_accel_x.values
//        val entries_thingy_accel_y = dataSet_thingy_accel_y.values
//        val entries_thingy_accel_z = dataSet_thingy_accel_z.values
//
//        val entries_thingy_gyro_x = dataSet_thingy_gyro_x.values
//        val entries_thingy_gyro_y = dataSet_thingy_gyro_y.values
//        val entries_thingy_gyro_z = dataSet_thingy_gyro_z.values
//
//        // Create a ByteBuffer to hold the float values for input to the TFLite model
//        val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(4 * 25 * 6)
//
//        // Populate the ByteBuffer, where y is the reading and x is the time
//        val startIndex = dataSet_thingy_accel_x.entryCount - 25
//        for (i in startIndex until startIndex + 25) {
//            inputBuffer.putFloat(entries_thingy_accel_x[i].y)
//            inputBuffer.putFloat(entries_thingy_accel_y[i].y)
//            inputBuffer.putFloat(entries_thingy_accel_z[i].y)
//
//            inputBuffer.putFloat(entries_thingy_gyro_x[i].y)
//            inputBuffer.putFloat(entries_thingy_gyro_y[i].y)
//            inputBuffer.putFloat(entries_thingy_gyro_z[i].y)
//        }
//
//        // Prepare the output buffer
//        val outputBuffer: ByteBuffer = ByteBuffer.allocateDirect(48)
////        outputBuffer.order(ByteOrder.nativeOrder())
//
//        // Run inference using TensorFlow Lite
//        tflite_thingy.run(inputBuffer, outputBuffer)
//
//        // Rewind the buffer to the beginning so we can read from it
//        outputBuffer.rewind()
//
//        // Convert ByteBuffer to FloatBuffer for easier handling
//        val floatBuffer: FloatBuffer = outputBuffer.asFloatBuffer()
//
//        // Initialize variables to keep track of the maximum value and corresponding index
//        var maxValue = floatBuffer.get(0)
//        var outputIndex = 0
//
//        // Iterate through the output buffer to find the maximum value and index
//        for (i in 1 until floatBuffer.limit()) {
//            val currentValue = floatBuffer.get(i)
//            if (currentValue > maxValue) {
//                maxValue = currentValue
//                outputIndex = i
//            }
//        }
//
//        Log.i("Model", outputIndex.toString())
//
//        val currentActivity = activities[outputIndex]
//        // Update the TextView
//        (findViewById<TextView>(R.id.currentAct)).text = "Current Activity: $currentActivity"
//
//    }

    fun runModelWithResAvailableData() {
        // Make sure you have enough data
        if (dataSet_res_accel_x.entryCount < 50
            || dataSet_res_accel_x.entryCount % 25 != 0
        ) {
            return
        }

        val entries_res_accel_x = dataSet_res_accel_x.values
        val entries_res_accel_y = dataSet_res_accel_y.values
        val entries_res_accel_z = dataSet_res_accel_z.values

        val entries_res_gyro_x = dataSet_res_gyro_x.values
        val entries_res_gyro_y = dataSet_res_gyro_y.values
        val entries_res_gyro_z = dataSet_res_gyro_z.values

        // Create a ByteBuffer to hold the float values for input to the TFLite model
        val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(4 * 50 * 3)
//        val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(4 * 50 * 6)

        // Populate the ByteBuffer, where y is the reading and x is the time
        val startIndex = dataSet_res_accel_x.entryCount - 50
        for (i in startIndex until startIndex + 50) {
            inputBuffer.putFloat(entries_res_accel_x[i].y)
            inputBuffer.putFloat(entries_res_accel_y[i].y)
            inputBuffer.putFloat(entries_res_accel_z[i].y)

//            // Normalize the y-values of the gyro data
//            val normalizedGyroX = (entries_res_gyro_x[i].y - minVal) / (maxVal - minVal)
//            val normalizedGyroY = (entries_res_gyro_y[i].y - minVal) / (maxVal - minVal)
//            val normalizedGyroZ = (entries_res_gyro_z[i].y - minVal) / (maxVal - minVal)
//
//            // Now put these normalized values into the inputBuffer
//            inputBuffer.putFloat(normalizedGyroX)
//            inputBuffer.putFloat(normalizedGyroY)
//            inputBuffer.putFloat(normalizedGyroZ)
        }

        // Prepare the output buffer
        val outputBuffer: ByteBuffer = ByteBuffer.allocateDirect(12 * 4)
//        val outputBuffer: ByteBuffer = ByteBuffer.allocateDirect(44 * 4)
//        outputBuffer.order(ByteOrder.nativeOrder())

        // Run inference using TensorFlow Lite
        tflite_res.run(inputBuffer, outputBuffer)

        // Rewind the buffer to the beginning so we can read from it
        outputBuffer.rewind()

        // Convert ByteBuffer to FloatBuffer for easier handling
        val floatBuffer: FloatBuffer = outputBuffer.asFloatBuffer()

        // Initialize variables to keep track of the maximum value and corresponding index
        var maxValue = floatBuffer.get(0)
        var outputIndex = 0

        // Iterate through the output buffer to find the maximum value and index
        for (i in 1 until floatBuffer.limit()) {
            val currentValue = floatBuffer.get(i)
            if (currentValue > maxValue) {
                maxValue = currentValue
                outputIndex = i
            }
        }

        Log.i("Model", outputIndex.toString())
        val currentActivity = activities[outputIndex].toString()
        val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, MODE_PRIVATE)
        val isRecording = sharedPreferences.getBoolean("recording", false)
        if (pastActivity == "") {
            pastActivity = currentActivity
            findViewById<TextView>(R.id.currentAct).text = "Current Activity: $currentActivity"
            if (isRecording) {
                recordData(currentActivity)
            } else {
                // don't do anything
            }

        } else if (pastActivity == currentActivity) {
            findViewById<TextView>(R.id.currentAct).text = "Current Activity: $currentActivity"
            if (isRecording) {
                recordData(currentActivity)
            } else {
                // don't do anything
            }
        } else {
            pastActivity = currentActivity
        }

    }


    fun recordData(activity: String) {
        val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, MODE_PRIVATE)
        val classifiedLabel = "ascending stairs normal"
        val username = sharedPreferences.getString("username", "").toString()
        val currentDate = System.currentTimeMillis()
        val durationInSeconds = 2L

        val existingActivityData = historyDB.getActivityData(username, currentDate, currentDate)

        if (existingActivityData.isNotEmpty()) {
            val existingDuration = existingActivityData.first().durationInSeconds
            val updatedDuration = existingDuration + durationInSeconds

            // Update the existing record in the database
            historyDB.updateActivityData(username, currentDate, classifiedLabel, updatedDuration)
        } else {
            // If no record exists, insert a new record
            val activityData = HistoryDB.ActivityData(username, currentDate, classifiedLabel, durationInSeconds)
            historyDB.insertActivityData(activityData)
        }
    }

    fun setupCharts() {
        respeckChart = findViewById(R.id.respeck_chart)
        thingyChart = findViewById(R.id.thingy_chart)

        // Respeck

        time = 0f
        val entries_res_accel_x = ArrayList<Entry>()
        val entries_res_accel_y = ArrayList<Entry>()
        val entries_res_accel_z = ArrayList<Entry>()

        val entries_res_gyro_x = ArrayList<Entry>()
        val entries_res_gyro_y = ArrayList<Entry>()
        val entries_res_gyro_z = ArrayList<Entry>()

        dataSet_res_accel_x = LineDataSet(entries_res_accel_x, "Accel X")
        dataSet_res_accel_y = LineDataSet(entries_res_accel_y, "Accel Y")
        dataSet_res_accel_z = LineDataSet(entries_res_accel_z, "Accel Z")

        dataSet_res_gyro_x = LineDataSet(entries_res_gyro_x, "Gyro X")
        dataSet_res_gyro_y = LineDataSet(entries_res_gyro_y, "Gyro Y")
        dataSet_res_gyro_z = LineDataSet(entries_res_gyro_z, "Gyro Z")

        dataSet_res_accel_x.setDrawCircles(false)
        dataSet_res_accel_y.setDrawCircles(false)
        dataSet_res_accel_z.setDrawCircles(false)

        dataSet_res_gyro_x.setDrawCircles(false)
        dataSet_res_gyro_y.setDrawCircles(false)
        dataSet_res_gyro_z.setDrawCircles(false)

        dataSet_res_accel_x.setColor(
            ContextCompat.getColor(
                this,
                R.color.red
            )
        )
        dataSet_res_accel_y.setColor(
            ContextCompat.getColor(
                this,
                R.color.green
            )
        )
        dataSet_res_accel_z.setColor(
            ContextCompat.getColor(
                this,
                R.color.blue
            )
        )

        val dataSetsRes = ArrayList<ILineDataSet>()
        dataSetsRes.add(dataSet_res_accel_x)
        dataSetsRes.add(dataSet_res_accel_y)
        dataSetsRes.add(dataSet_res_accel_z)

        allRespeckData = LineData(dataSetsRes)
        respeckChart.data = allRespeckData
        respeckChart.invalidate()

        // Thingy

        time = 0f
        val entries_thingy_accel_x = ArrayList<Entry>()
        val entries_thingy_accel_y = ArrayList<Entry>()
        val entries_thingy_accel_z = ArrayList<Entry>()

        val entries_thingy_gyro_x = ArrayList<Entry>()
        val entries_thingy_gyro_y = ArrayList<Entry>()
        val entries_thingy_gyro_z = ArrayList<Entry>()

        dataSet_thingy_accel_x = LineDataSet(entries_thingy_accel_x, "Accel X")
        dataSet_thingy_accel_y = LineDataSet(entries_thingy_accel_y, "Accel Y")
        dataSet_thingy_accel_z = LineDataSet(entries_thingy_accel_z, "Accel Z")

        dataSet_thingy_gyro_x = LineDataSet(entries_thingy_gyro_x, "Gyro X")
        dataSet_thingy_gyro_y = LineDataSet(entries_thingy_gyro_y, "Gyro Y")
        dataSet_thingy_gyro_z = LineDataSet(entries_thingy_gyro_z, "Gyro Z")

        dataSet_thingy_accel_x.setDrawCircles(false)
        dataSet_thingy_accel_y.setDrawCircles(false)
        dataSet_thingy_accel_z.setDrawCircles(false)

        dataSet_thingy_gyro_x.setDrawCircles(false)
        dataSet_thingy_gyro_y.setDrawCircles(false)
        dataSet_thingy_gyro_z.setDrawCircles(false)

        dataSet_thingy_accel_x.setColor(
            ContextCompat.getColor(
                this,
                R.color.red
            )
        )
        dataSet_thingy_accel_y.setColor(
            ContextCompat.getColor(
                this,
                R.color.green
            )
        )
        dataSet_thingy_accel_z.setColor(
            ContextCompat.getColor(
                this,
                R.color.blue
            )
        )

        val dataSetsThingy = ArrayList<ILineDataSet>()
        dataSetsThingy.add(dataSet_thingy_accel_x)
        dataSetsThingy.add(dataSet_thingy_accel_y)
        dataSetsThingy.add(dataSet_thingy_accel_z)

        allThingyData = LineData(dataSetsThingy)
        thingyChart.data = allThingyData
        thingyChart.invalidate()
    }

    fun updateGraph(graph: String, x: Float, y: Float, z: Float) {
        // take the first element from the queue
        // and update the graph with it
        if (graph == "respeck") {
            dataSet_res_accel_x.addEntry(Entry(time, x))
            dataSet_res_accel_y.addEntry(Entry(time, y))
            dataSet_res_accel_z.addEntry(Entry(time, z))

            runOnUiThread {
                allRespeckData.notifyDataChanged()
                respeckChart.notifyDataSetChanged()
                respeckChart.invalidate()
                respeckChart.setVisibleXRangeMaximum(150f)
                respeckChart.moveViewToX(respeckChart.lowestVisibleX + 40)
            }
        } else if (graph == "thingy") {
            dataSet_thingy_accel_x.addEntry(Entry(time, x))
            dataSet_thingy_accel_y.addEntry(Entry(time, y))
            dataSet_thingy_accel_z.addEntry(Entry(time, z))

            runOnUiThread {
                allThingyData.notifyDataChanged()
                thingyChart.notifyDataSetChanged()
                thingyChart.invalidate()
                thingyChart.setVisibleXRangeMaximum(150f)
                thingyChart.moveViewToX(thingyChart.lowestVisibleX + 40)
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(respeckLiveUpdateReceiver)
        unregisterReceiver(thingyLiveUpdateReceiver)
        looperRespeck.quit()
        looperThingy.quit()
    }
}
