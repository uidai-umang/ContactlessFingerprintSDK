package app.gov.uidai.capture.ui.guideline

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import app.gov.uidai.capture.ui.camera.CameraFragment
import app.gov.uidai.capture.ui.guideline.adapter.GuidelineAdapter
import app.gov.uidai.capture.ui.guideline.adapter.GuidelineItem
import app.gov.uidai.capture.BuildConfig
import app.gov.uidai.capture.R
import app.gov.uidai.capture.databinding.FragmentGuidelineBinding
import `in`.gov.uidai.utility.constants.ResultCode

class GuidelineFragment : Fragment() {

    private var _binding: FragmentGuidelineBinding? = null
    private val binding: FragmentGuidelineBinding get() = _binding!!

    private val navArgs: GuidelineFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGuidelineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvGuideline.layoutManager = LinearLayoutManager(requireContext())
        binding.rvGuideline.adapter = GuidelineAdapter(requireContext(), GUIDELINE_ITEMS)

        if (BuildConfig.DEBUG) {
            binding.btnDebugSettings.visibility = View.VISIBLE
            binding.btnDebugSettings.setOnClickListener {

                findNavController().navigate(
                    R.id.action_guidelineFragment_to_debugSettingsFragment
                )
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.checkboxConsent.setOnCheckedChangeListener { _, isChecked ->
            binding.btnProceed.apply {
                isEnabled = isChecked
            }
        }

        binding.btnProceed.setOnClickListener {
            binding.btnProceed.apply {
                isEnabled = false
                text = requireContext().getString(R.string.btn_loading_camera)
            }.post {
                try {
                    val action = GuidelineFragmentDirections.actionGuidelineFragmentToCameraFragment(
                        navArgs.txnId
                    )
                    findNavController().navigate(action)
                } catch (e: Exception) {
                    binding.btnProceed.apply {
                        isEnabled = binding.checkboxConsent.isChecked
                        text = requireContext().getString(R.string.btn_proceed)
                    }
                    Log.e(TAG, "Error while loading camera: ${e.printStackTrace()}")
                }
            }
        }
    }

    private fun finish(
        resultCode: Int = ResultCode.CAPTURE_USER_ABORT,
        data: String? = null
    ) {
        val activity = activity ?: return
        if (!activity.isFinishing && !activity.isDestroyed) {
            Log.i(TAG, "finish()")
            requireActivity().supportFragmentManager.setFragmentResult(
                CameraFragment.Companion.CAPTURE_FRAGMENT_RESULT,
                bundleOf(
                    (CameraFragment.Companion.KEY_RESULT_CODE to resultCode),
                    (CameraFragment.Companion.KEY_FINAL_IMAGE to data)
                )
            )
        }
    }

    companion object {
        private val TAG = GuidelineFragment::class.simpleName

        private val GUIDELINE_ITEMS: List<GuidelineItem> = listOf(
            GuidelineItem(R.string.guideline1, R.drawable.ic_guideline1),
            GuidelineItem(R.string.guideline2, R.drawable.ic_guideline2),
            GuidelineItem(R.string.guideline3, R.drawable.ic_guideline3),
            GuidelineItem(R.string.guideline4, R.drawable.ic_guideline4),
            GuidelineItem(R.string.guideline5, R.drawable.ic_guideline5)
        )
    }
}