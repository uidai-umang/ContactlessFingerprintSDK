package app.gov.uidai.capture.ui.guideline.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.gov.uidai.capture.R

data class GuidelineItem(
    val stringId: Int,
    val iconResId: Int
)

class GuidelineAdapter(private val context: Context, private val guidelineItems: List<GuidelineItem>) :
    RecyclerView.Adapter<GuidelineAdapter.GuidelineItemViewHolder>() {

    inner class GuidelineItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconView: ImageView = itemView.findViewById(R.id.guidelineIcon)
        val text: TextView = itemView.findViewById(R.id.guidelineText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuidelineItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_guideline, parent, false)
        return GuidelineItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: GuidelineItemViewHolder, position: Int) {
        val item = guidelineItems[position]
        holder.text.setText(item.stringId)
        holder.iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        holder.iconView.setImageResource(item.iconResId)
    }

    override fun getItemCount() = guidelineItems.size
}
