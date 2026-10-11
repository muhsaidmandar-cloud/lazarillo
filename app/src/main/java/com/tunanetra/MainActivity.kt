package com.tunanetra

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val box = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(32,32,32,32) }
        box.addView(TextView(this).apply { text="Lazarillo"; textSize=26f; setTextColor(Color.BLACK) })
        box.addView(TextView(this).apply { text="Aplikasi Android Kotlin siap dikembangkan." })
        setContentView(box)
    }
}
