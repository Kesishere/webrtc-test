package com.kes.test

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kes.test.databinding.MainBinding
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.PermissionRequest


class MainActivity : AppCompatActivity() {
    private lateinit var binding: MainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val perms = arrayOf<String>(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        EasyPermissions.requestPermissions(
            PermissionRequest.Builder(
                this, 22, Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
                .setPositiveButtonText("OK")
                .setNegativeButtonText("CANCEL")
                .build()
        )

        binding.btn.setOnClickListener {
            conntect()
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    private fun conntect() {
        val intent = Intent(this, Connect::class.java)
        intent.putExtra("ROOM", binding.et.text.toString())
        startActivity(intent)
    }


}