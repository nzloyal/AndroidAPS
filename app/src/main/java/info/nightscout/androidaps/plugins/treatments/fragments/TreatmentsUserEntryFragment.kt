package info.nightscout.androidaps.plugins.treatments.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.*
import info.nightscout.androidaps.databinding.TreatmentsUserEntryFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsUserEntryItemBinding
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.extensions.*
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class TreatmentsUserEntryFragment : DaggerFragment() {

    @Inject lateinit var repository: AppRepository
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var translator: Translator

    private val disposable = CompositeDisposable()

    private var _binding: TreatmentsUserEntryFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsUserEntryFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)

        disposable += repository
            .getAllUserEntries()
            .observeOn(aapsSchedulers.main)
            .subscribe { list -> binding.recyclerview.swapAdapter(UserEntryAdapter(list), true) }
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    inner class UserEntryAdapter internal constructor(var entries: List<UserEntry>) : RecyclerView.Adapter<UserEntryAdapter.UserEntryViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserEntryViewHolder {
            val view: View = LayoutInflater.from(parent.context).inflate(R.layout.treatments_user_entry_item, parent, false)
            return UserEntryViewHolder(view)
        }

        override fun onBindViewHolder(holder: UserEntryViewHolder, position: Int) {
            val current = entries[position]
            holder.binding.date.text = dateUtil.dateAndTimeAndSecondsString(current.timestamp)
            holder.binding.action.text = resourceHelper.gs(current.action.stringId())
            if (current.s != "") holder.binding.s.text = current.s else holder.binding.s.visibility = View.GONE
            //holder.binding.s.text = current.toString()  //for debug
            var valuesWithUnitString = ""
            var rStringParam = 0
            for(v in current.values) {
                if (rStringParam >0)
                    rStringParam--
                else
                    when (v.unit) {
                        Units.Timestamp -> valuesWithUnitString += dateUtil.dateAndTimeAndSecondsString(v.lValue) + " "
                        Units.CPEvent -> valuesWithUnitString += translator.translate(v.sValue) + " "
                        Units.R_String -> {
                            rStringParam = v.lValue.toInt()
                            when (rStringParam) {   //
                                0 -> valuesWithUnitString += resourceHelper.gs(v.iValue) + " "
                                1 -> valuesWithUnitString += resourceHelper.gs(v.iValue, current.values[current.values.indexOf(v)+1].value()) + " "
                                2 -> valuesWithUnitString += resourceHelper.gs(v.iValue, current.values[current.values.indexOf(v)+1].value(), current.values[current.values.indexOf(v)+2].value()) + " "
                                3 -> valuesWithUnitString += resourceHelper.gs(v.iValue, current.values[current.values.indexOf(v)+1].value(), current.values[current.values.indexOf(v)+2].value(), current.values[current.values.indexOf(v)+3].value()) + " "
                                4 -> rStringParam = 0
                            }
                        }
                        Units.Mg_Dl -> valuesWithUnitString += if (profileFunction.getUnits()==Constants.MGDL) DecimalFormatter.to0Decimal(v.dValue) + resourceHelper.gs(Units.Mg_Dl.stringId()) + " " else DecimalFormatter.to1Decimal(v.dValue/Constants.MMOLL_TO_MGDL) + resourceHelper.gs(Units.Mmol_L.stringId()) + " "
                        Units.Mmol_L -> valuesWithUnitString += if (profileFunction.getUnits()==Constants.MGDL) DecimalFormatter.to0Decimal(v.dValue*Constants.MMOLL_TO_MGDL) + resourceHelper.gs(Units.Mg_Dl.stringId()) + " " else DecimalFormatter.to1Decimal(v.dValue) + resourceHelper.gs(Units.Mmol_L.stringId()) + " "
                        Units.G -> valuesWithUnitString += DecimalFormatter.to0Decimal(v.dValue) + resourceHelper.gs(Units.G.stringId()) + " "
                        Units.U_H -> valuesWithUnitString += DecimalFormatter.to2Decimal(v.dValue) + resourceHelper.gs(Units.U_H.stringId()) + " "
                        else -> valuesWithUnitString += if (!v.value().equals(0) && !v.value().equals("")) { v.value().toString() + if (!v.unit.stringId().equals(0)) resourceHelper.gs(v.unit.stringId()) + " " else " " } else ""
                    }
            }
            if (current.values.size > 0)
                holder.binding.values.visibility = View.VISIBLE
                holder.binding.values.text = valuesWithUnitString
        }

        inner class UserEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsUserEntryItemBinding.bind(itemView)
        }

        override fun getItemCount(): Int = entries.size

    }
}