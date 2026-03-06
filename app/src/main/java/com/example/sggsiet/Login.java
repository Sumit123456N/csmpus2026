package com.example.sggsiet;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.sggsiet.AdminModule.PrincipalDashboard;
import com.example.sggsiet.DocterModule.DocterDashboard;
import com.example.sggsiet.StudentModule.GardLogin;
import com.example.sggsiet.StudentModule.StudentDashboard;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Random;

public class Login extends AppCompatActivity {

    private AutoCompleteTextView spinnerLoginType;
    private EditText etEmail, etOtp;
    private MaterialButton btnLogin;
    private MaterialTextView btnGetOtp,tvgard;
    private FirebaseAuth auth;
    private DatabaseReference databaseReference;
    private SharedPreferences sharedPreferences;
    private String generatedOtp;
    private static final int SMS_PERMISSION_CODE = 101;
    private static final String DEFAULT_PASSWORD = "Default@123";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("Users");
        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);

        spinnerLoginType = findViewById(R.id.spinnerLoginType);
        etEmail = findViewById(R.id.etEmail);
        etOtp = findViewById(R.id.etOtp);
        btnLogin = findViewById(R.id.btnLogin);
        btnGetOtp = findViewById(R.id.tvGetOtp);
        tvgard=findViewById(R.id.gardtv);

        tvgard.setOnClickListener(v -> {
            Intent intent = new Intent(Login.this, GardLogin.class);
            startActivity(intent);


                });

        String[] userTypes = {"Administration", "Faculty", "Doctor", "Student"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, userTypes);
        spinnerLoginType.setAdapter(adapter);

        btnGetOtp.setOnClickListener(v -> sendOtp());
        btnLogin.setOnClickListener(v -> verifyOtp());
    }

    private void sendOtp() {
        String email = etEmail.getText().toString().trim();
        String userType = spinnerLoginType.getText().toString();

        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter an email", Toast.LENGTH_SHORT).show();
            return;
        }

        if (userType.equals("Student")) {
            fetchPhoneNumberFromCsv(email, "dummy_student_data.csv", true);
        } else if (userType.equals("Faculty")) {
            fetchPhoneNumberFromCsv(email, "dummy_faculty_data.csv", false);
        } else {
            String phoneNumber = getStaticPhoneNumber(userType, email);
            if (phoneNumber.isEmpty()) {
                Toast.makeText(this, "Invalid email for selected role", Toast.LENGTH_SHORT).show();
                return;
            }
            generateAndSendOtp(phoneNumber);
        }
    }

    private void fetchPhoneNumberFromCsv(String email, String fileName, boolean isStudent) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference(fileName);

        storageRef.getBytes(Long.MAX_VALUE).addOnSuccessListener(bytes -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(bytes)))) {
                String line;
                boolean found = false;

                while ((line = reader.readLine()) != null) {
                    String[] columns = line.split(",");

                    if (isStudent) {
                        // Validate Student CSV Format (Minimum 6 Columns Required)
                        if (columns.length < 7) continue;

                        String csvEmail = columns[2].trim();  // Student email
                        String phoneNumber = columns[5].trim();
                        String dept=columns[3];// Student mobile

                        if (csvEmail.equalsIgnoreCase(email.trim())) {
                            found = true;
                            if (!phoneNumber.isEmpty()) {
                                saveStudentDetailsToPreferences(columns);
                                generateAndSendOtp(phoneNumber);
                            } else {
                                Toast.makeText(this, "Phone number not found for student", Toast.LENGTH_SHORT).show();
                            }
                            break;
                        }
                    } else {
                        // Validate Faculty CSV Format (Minimum 5 Columns Required)
                        if (columns.length < 5) continue;

                        String csvEmail = columns[0].trim();  // Faculty email
                        String phoneNumber = columns[2].trim();
                        String dept=columns[3];// Faculty mobile

                        if (csvEmail.equalsIgnoreCase(email.trim())) {
                            found = true;
                            if (!phoneNumber.isEmpty()) {
                                saveFacultyDetailsToPreferences(columns);
                                generateAndSendOtp(phoneNumber);
                            } else {
                                Toast.makeText(this, "Phone number not found for faculty", Toast.LENGTH_SHORT).show();
                            }
                            break;
                        }
                    }
                }

                if (!found) {
                    Toast.makeText(this, "Email not found in records", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error reading data", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to fetch data", Toast.LENGTH_SHORT).show();
        });
    }


    private void generateAndSendOtp(String phoneNumber) {
        generatedOtp = String.valueOf(new Random().nextInt(899999) + 100000);
        requestSmsPermission(phoneNumber, generatedOtp);
    }

    private void requestSmsPermission(String phoneNumber, String otp) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            sendSms(phoneNumber, otp);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, SMS_PERMISSION_CODE);
        }
    }

    private void sendSms(String phoneNumber, String otp) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, "Your OTP is: " + otp, null, null);
            Toast.makeText(this, "OTP sent successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to send OTP", Toast.LENGTH_SHORT).show();
        }
    }

    private void verifyOtp() {
        String enteredOtp = etOtp.getText().toString().trim();

        if (generatedOtp == null || !generatedOtp.equals(enteredOtp)) {
            Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show();
            return;
        }

        String email = etEmail.getText().toString().trim();
        String userType = spinnerLoginType.getText().toString();

        saveUserSession(userType);
        navigateToDashboard(userType);
    }

    private void saveUserSession(String userType) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isLoggedIn", true);
        editor.putString("userType", userType);
        editor.apply();
    }

    private void navigateToDashboard(String userType) {
        Intent intent;

        switch (userType) {
            case "Administration":
                intent = new Intent(this, PrincipalDashboard.class);
                break;
            case "Faculty":
                intent = new Intent(this, FacultyModuleActivity.class);
                break;
            case "Doctor":
                intent = new Intent(this, DocterDashboard.class);
                break;
            case "Student":
                intent = new Intent(this, StudentDashboard.class);
                break;
            default:
                Toast.makeText(this, "Invalid user type", Toast.LENGTH_SHORT).show();
                return;
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void saveStudentDetailsToPreferences(String[] columns) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("studentEmail", columns[2]);
        editor.putString("studentName", columns[1]);
        editor.putString("studentMobile", columns[5]);
        editor.putString("studentDept", columns[3]);
        editor.putString("enrollmentNo", columns[0]);
        editor.putString("studentDepartment", columns[0]);
        editor.apply();
    }

    private void saveFacultyDetailsToPreferences(String[] columns) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("facultyEmail", columns[0]);
        editor.putString("facultyName", columns[1]);
        editor.putString("facultyMobile", columns[2]);
        editor.putString("facultyDept", columns[3]);
        editor.apply();
    }

    private String getStaticPhoneNumber(String userType, String email) {
        HashMap<String, String> phoneNumbers = new HashMap<>();
        phoneNumbers.put("Administration_admin@gmail.com", "7028659544");
        phoneNumbers.put("Doctor_doctor@gmail.com", "7028659544");

        return phoneNumbers.getOrDefault(userType + "_" + email, "");
    }
}
