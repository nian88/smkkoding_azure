package id.smkcoding.smkpelitanusantara.kediriapp

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox

class TodoItemAdapter(var mContext: Context, var mLayoutResourceId: Int) : ArrayAdapter<TodoItem>(mContext, mLayoutResourceId) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var row = convertView

        val currentItem = getItem(position)

        if (row == null) {
            val inflater = (mContext as Activity).layoutInflater
            row = inflater.inflate(mLayoutResourceId, parent, false)
        }

        row!!.tag = currentItem
        val checkBox = row.findViewById<View>(R.id.checkToDoItem) as CheckBox
        checkBox.text = currentItem!!.text
        checkBox.isChecked = false
        checkBox.isEnabled = true

        checkBox.setOnClickListener {
            if (checkBox.isChecked) {
                checkBox.isEnabled = false
                if (mContext is MainActivity) {
                    val activity = mContext as MainActivity
                    activity.checkItem(currentItem)
                }
            }
        }
        return row
    }

}