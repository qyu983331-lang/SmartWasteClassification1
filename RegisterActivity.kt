package com.example.smartwasteclassification

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smartwasteclassification.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)

        binding.btnRegister.setOnClickListener {
            val username = binding.etRegUsername.text.toString().trim()
            val nicknameInput = binding.etRegNickname.text.toString().trim()
            val password = binding.etRegPassword.text.toString().trim()
            val confirmPassword = binding.etRegPasswordConfirm.text.toString().trim()

            if (username.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty()) {
                if (password == confirmPassword) {
                    // 如果昵称为空，则使用用户名作为默认昵称
                    val nickname = if (nicknameInput.isNotEmpty()) nicknameInput else username
                    val user = User(username = username, password = password, nickname = nickname)
                    val result = dbHelper.addUser(user)
                    if (result != -1L) {
                        Toast.makeText(this, "注册成功", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "注册失败，用户名可能已存在", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "两次输入的密码不一致", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "请填写所有字段", Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvBackToLogin.setOnClickListener {
            finish()
        }
    }
}
