// This file is part of the "Irrlicht Engine".
// For conditions of distribution and use, see copyright notice in irrlicht.h

#include "android_tools.h"
#include "../gframe/game.h"
#include "../gframe/bufferio.h"

namespace irr {
namespace android {

inline static void ReadString(irr::io::path &path, char*& p) {
	int length = BufferIO::ReadInt32(p);
	if (length != 0) {
		path.append(p, length);
		path[length] = '\0';
		p += length;
	}
}

InitOptions::InitOptions(void*data) :
		m_opengles_version(0), m_card_quality(0), m_font_aa_enabled(TRUE), m_se_enabled(
				TRUE) {
	if (data != NULL) {
		char* rawdata = (char*)data;
		int tmplength = 0;
		m_opengles_version = BufferIO::ReadInt32(rawdata);
		m_se_enabled = BufferIO::ReadInt32(rawdata) > 0;
		
		m_card_quality = BufferIO::ReadInt32(rawdata);
		m_font_aa_enabled = BufferIO::ReadInt32(rawdata) > 0;
		m_ps_enabled = BufferIO::ReadInt32(rawdata) > 0;
		
		//cache dir
		ReadString(m_work_dir, rawdata);
		//cdbs
		cdb_count = BufferIO::ReadInt32(rawdata);
		m_db_files = new io::path[cdb_count];
		
		for(int i = 0;i < cdb_count; i++){
			io::path tmp_path;
			ReadString(tmp_path, rawdata);
			m_db_files[i] = tmp_path;
		}
		//zips
		zip_count = BufferIO::ReadInt32(rawdata);
		m_archive_files = new io::path[zip_count];
		for(int i = 0 ;i < zip_count; i++){
			io::path tmp_path;
			ReadString(tmp_path, rawdata);
			m_archive_files[i] = tmp_path;
		}
	}
}

irr::io::path getExternalStorageDir(ANDROID_APP app) {
	irr::io::path ret;
	if (!app || !app->activity || !app->activity->vm)
		return ret;

	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return ret;
	jclass classEnvironment = jni->FindClass("android/os/Environment");
	jclass classFile = jni->FindClass("java/io/File");
	if (!classEnvironment || !classFile) {
		app->activity->vm->DetachCurrentThread();
		return ret;
	}
	jmethodID evMethod = jni->GetStaticMethodID(classEnvironment,
			"getExternalStorageDirectory", "()Ljava/io/File;");
	jobject retFromJava = jni->CallStaticObjectMethod(classEnvironment,
			evMethod);
	jni->DeleteLocalRef(classEnvironment);
	jmethodID fileMethod = jni->GetMethodID(classFile, "getAbsolutePath",
			"()Ljava/lang/String;");
	jstring retString = (jstring) jni->CallObjectMethod(retFromJava,
			fileMethod);
	jni->DeleteLocalRef(classFile);
	const char* chars = jni->GetStringUTFChars(retString, NULL);
	ret.append(chars);
	jni->ReleaseStringUTFChars(retString, chars);
	app->activity->vm->DetachCurrentThread();
	return ret;
}

irr::io::path getExternalFilesDir(ANDROID_APP app) {
	irr::io::path ret;
	if (!app || !app->activity || !app->activity->vm)
		return ret;

	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return ret;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);

	jmethodID evMethod = jni->GetMethodID(classApp, "getCompatExternalFilesDir",
			"()Ljava/lang/String;");
	jstring retString = (jstring) jni->CallObjectMethod(application, evMethod);
	jni->DeleteLocalRef(ClassNativeActivity);
	jni->DeleteLocalRef(classApp);

	const char* chars = jni->GetStringUTFChars(retString, NULL);
	ret.append(chars);
	jni->ReleaseStringUTFChars(retString, chars);
	app->activity->vm->DetachCurrentThread();
	return ret;
}

float getScreenHeight(ANDROID_APP app) {
	float ret;
	if (!app || !app->activity || !app->activity->vm)
		return ret;

	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return ret;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);
	jmethodID resdirMethod = jni->GetMethodID(classApp, "getScreenHeight",
			"()F");
	ret = jni->CallFloatMethod(application, resdirMethod);
	jni->DeleteLocalRef(classApp);
	jni->DeleteLocalRef(ClassNativeActivity);
	app->activity->vm->DetachCurrentThread();
	return ret;
}

float getScreenWidth(ANDROID_APP app) {
	float ret;
	if (!app || !app->activity || !app->activity->vm)
		return ret;

	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return ret;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);
	jmethodID resdirMethod = jni->GetMethodID(classApp, "getScreenWidth",
			"()F");
	ret = jni->CallFloatMethod(application, resdirMethod);
	jni->DeleteLocalRef(classApp);
	jni->DeleteLocalRef(ClassNativeActivity);
	app->activity->vm->DetachCurrentThread();
	return ret;
}

irr::io::path getDBDir(ANDROID_APP app) {
	irr::io::path ret;
	if (!app || !app->activity || !app->activity->vm)
		return ret;

	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return ret;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);
	jmethodID resdirMethod = jni->GetMethodID(classApp, "getDataBasePath",
			"()Ljava/lang/String;");
	jstring retString = (jstring) jni->CallObjectMethod(application,
			resdirMethod);
	jni->DeleteLocalRef(classApp);
	jni->DeleteLocalRef(ClassNativeActivity);
	const char* chars = jni->GetStringUTFChars(retString, NULL);
	ret.append(chars);
	jni->ReleaseStringUTFChars(retString, chars);
	app->activity->vm->DetachCurrentThread();
	return ret;
}

irr::io::path getCardImagePath(ANDROID_APP app) {
	irr::io::path ret;
	if (!app || !app->activity || !app->activity->vm)
		return ret;

	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return ret;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);
	jmethodID resdirMethod = jni->GetMethodID(classApp, "getCardImagePath",
			"()Ljava/lang/String;");
	jstring retString = (jstring) jni->CallObjectMethod(application,
			resdirMethod);
	jni->DeleteLocalRef(classApp);
	jni->DeleteLocalRef(ClassNativeActivity);
	const char* chars = jni->GetStringUTFChars(retString, NULL);
	ret.append(chars);
	jni->ReleaseStringUTFChars(retString, chars);
	app->activity->vm->DetachCurrentThread();
	return ret;
}

irr::io::path getCoreConfigVersion(ANDROID_APP app) {
	irr::io::path ret;
	if (!app || !app->activity || !app->activity->vm)
		return ret;

	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return ret;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);
	jmethodID resdirMethod = jni->GetMethodID(classApp, "getCoreConfigVersion",
			"()Ljava/lang/String;");
	jstring retString = (jstring) jni->CallObjectMethod(application,
			resdirMethod);
	jni->DeleteLocalRef(classApp);
	jni->DeleteLocalRef(ClassNativeActivity);
	const char* chars = jni->GetStringUTFChars(retString, NULL);
	ret.append(chars);
	jni->ReleaseStringUTFChars(retString, chars);
	app->activity->vm->DetachCurrentThread();
	return ret;
}

int getOpenglVersion(ANDROID_APP app) {
	int ret = 1;
	if (!app || !app->activity || !app->activity->vm)
		return ret;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return ret;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);
	jmethodID glversionMethod = jni->GetMethodID(classApp, "getOpenglVersion",
			"()I");
	ret = jni->CallIntMethod(application, glversionMethod);
	jni->DeleteLocalRef(classApp);
	jni->DeleteLocalRef(ClassNativeActivity);
	app->activity->vm->DetachCurrentThread();
	return ret;
}

int getCardQuality(ANDROID_APP app) {
	int ret = 1;
	if (!app || !app->activity || !app->activity->vm)
		return ret;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return ret;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);
	jmethodID glversionMethod = jni->GetMethodID(classApp, "getCardQuality",
			"()I");
	ret = jni->CallIntMethod(application, glversionMethod);
	jni->DeleteLocalRef(classApp);
	jni->DeleteLocalRef(ClassNativeActivity);
	app->activity->vm->DetachCurrentThread();
	return ret;
}

//Retrive font path.
irr::io::path getFontPath(ANDROID_APP app) {
	irr::io::path ret;
	if (!app || !app->activity || !app->activity->vm)
		return ret;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return ret;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);
	jmethodID fontPathMethod = jni->GetMethodID(classApp, "getFontPath",
			"()Ljava/lang/String;");
	jstring retString = (jstring) jni->CallObjectMethod(application,
			fontPathMethod);
	jni->DeleteLocalRef(classApp);
	jni->DeleteLocalRef(ClassNativeActivity);
	const char* chars = jni->GetStringUTFChars(retString, NULL);
	ret.append(chars);
	jni->ReleaseStringUTFChars(retString, chars);
	app->activity->vm->DetachCurrentThread();
	return ret;
}

irr::io::path getResourcePath(ANDROID_APP app) {
	irr::io::path ret;
	if (!app || !app->activity || !app->activity->vm)
		return ret;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return ret;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);
	jmethodID resPathMethod = jni->GetMethodID(classApp, "getResourcePath",
			"()Ljava/lang/String;");
	jstring retString = (jstring) jni->CallObjectMethod(application,
			resPathMethod);
	jni->DeleteLocalRef(classApp);
	jni->DeleteLocalRef(ClassNativeActivity);
	const char* chars = jni->GetStringUTFChars(retString, NULL);
	ret.append(chars);
	jni->ReleaseStringUTFChars(retString, chars);
	app->activity->vm->DetachCurrentThread();
	return ret;
}



//Retrive last deck name.
irr::io::path getLastDeck(ANDROID_APP app) {
	return getSetting(app, "lastdeck");
}

irr::io::path getSetting(ANDROID_APP app, const char* key) {
	irr::io::path ret;
	if (!app || !app->activity || !app->activity->vm)
		return ret;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return ret;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);
	jmethodID lastdeckMethod = jni->GetMethodID(classApp, "getSetting",
			"(Ljava/lang/String;)Ljava/lang/String;");
	jstring keystring = jni->NewStringUTF(key);
	jstring retString = (jstring) jni->CallObjectMethod(application,
			lastdeckMethod, keystring);
	if (keystring) {
		jni->DeleteLocalRef(keystring);
	}
	jni->DeleteLocalRef(classApp);
	jni->DeleteLocalRef(ClassNativeActivity);
	if(retString == NULL){
		return ret;
	}
	const char* chars = jni->GetStringUTFChars(retString, NULL);
	ret.append(chars);
	jni->ReleaseStringUTFChars(retString, chars);
	app->activity->vm->DetachCurrentThread();
	return ret;
}

//save last deck name.
void setLastDeck(ANDROID_APP app, const char* deckname) {
	saveSetting(app, "lastdeck", deckname);
}

int getIntSetting(ANDROID_APP app, const char* key,int defvalue){
	if (!app || !app->activity || !app->activity->vm)
		return defvalue;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return defvalue;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);
	jmethodID lastdeckMethod = jni->GetMethodID(classApp, "getIntSetting",
			"(Ljava/lang/String;I)I");
	jstring keystring = jni->NewStringUTF(key);
	jint ret = jni->CallIntMethod(application,
			lastdeckMethod, keystring, defvalue);
	if (keystring) {
		jni->DeleteLocalRef(keystring);
	}
	jni->DeleteLocalRef(classApp);
	jni->DeleteLocalRef(ClassNativeActivity);
	app->activity->vm->DetachCurrentThread();
	return (int)ret;
}

void saveIntSetting(ANDROID_APP app, const char* key, int value) {
	if (!app || !app->activity || !app->activity->vm)
		return;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);
	jmethodID setDeckMethod = jni->GetMethodID(classApp, "saveIntSetting",
			"(Ljava/lang/String;I)V");
	jstring keystring = jni->NewStringUTF(key);
	jni->CallVoidMethod(application, setDeckMethod, keystring, value);
	if (keystring) {
		jni->DeleteLocalRef(keystring);
	}
	jni->DeleteLocalRef(classApp);
	jni->DeleteLocalRef(ClassNativeActivity);
	app->activity->vm->DetachCurrentThread();
}

void saveSetting(ANDROID_APP app, const char* key, const char* value) {
	if (!app || !app->activity || !app->activity->vm)
		return;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);
	jmethodID setDeckMethod = jni->GetMethodID(classApp, "saveSetting",
			"(Ljava/lang/String;Ljava/lang/String;)V");
	jstring keystring = jni->NewStringUTF(key);
	jstring valuestring = jni->NewStringUTF(value);
	jni->CallVoidMethod(application, setDeckMethod, keystring, valuestring);
	if (keystring) {
		jni->DeleteLocalRef(keystring);
	}
	if (valuestring) {
		jni->DeleteLocalRef(valuestring);
	}
	jni->DeleteLocalRef(classApp);
	jni->DeleteLocalRef(ClassNativeActivity);
	app->activity->vm->DetachCurrentThread();
}

bool perfromTrick(ANDROID_APP app) {
	bool ret = true;
	if (!app || !app->activity || !app->activity->vm)
		return false;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return false;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodPerfromTrick = jni->GetMethodID(ClassNativeActivity,
			"performTrick", "()[B");
	jbyteArray array = (jbyteArray) jni->CallObjectMethod(lNativeActivity,
			MethodPerfromTrick);
	jbyte* pArray = (jbyte*) jni->GetByteArrayElements(array,
	JNI_FALSE);
	for (int i = 0; i < 16; i++) {
		if (signed_buff[i] != *(pArray + i)) {
			ret = false;
			break;
		}
	}
	jni->DeleteLocalRef(ClassNativeActivity);
	jni->ReleaseByteArrayElements(array, pArray, JNI_FALSE);
	app->activity->vm->DetachCurrentThread();
	return true;
}

bool getFontAntiAlias(ANDROID_APP app) {
	bool ret = true;
	if (!app || !app->activity || !app->activity->vm)
		return true;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return true;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);
	jmethodID MethodFontAntialias = jni->GetMethodID(classApp,
			"getFontAntialias", "()Z");
	jboolean isAntialias = jni->CallBooleanMethod(application,
			MethodFontAntialias);
	if (isAntialias > 0) {
		ret = true;
	} else {
		ret = false;
	}
	jni->DeleteLocalRef(ClassNativeActivity);
	jni->DeleteLocalRef(classApp);
	app->activity->vm->DetachCurrentThread();
	return ret;
}

void perfromHapticFeedback(ANDROID_APP app) {
	if (!app || !app->activity || !app->activity->vm)
		return;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodPerfromHaptic = jni->GetMethodID(ClassNativeActivity,
			"performHapticFeedback", "()V");
	jni->CallVoidMethod(lNativeActivity, MethodPerfromHaptic);
	app->activity->vm->DetachCurrentThread();
}

irr::io::path getCacheDir(ANDROID_APP app) {
	irr::io::path ret;
	if (!app || !app->activity || !app->activity->vm)
		return ret;

	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return ret;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->FindClass("android/app/Application");
	jclass classFile = jni->FindClass("java/io/File");

	jmethodID evMethod = jni->GetMethodID(classApp, "getCacheDir",
			"()Ljava/io/File;");
	jobject retFromJava = jni->CallObjectMethod(application, evMethod);
	jni->DeleteLocalRef(ClassNativeActivity);
	jni->DeleteLocalRef(classApp);
	jmethodID fileMethod = jni->GetMethodID(classFile, "getAbsolutePath",
			"()Ljava/lang/String;");
	jstring retString = (jstring) jni->CallObjectMethod(retFromJava,
			fileMethod);
	jni->DeleteLocalRef(classFile);
	const char* chars = jni->GetStringUTFChars(retString, NULL);
	ret.append(chars);
	jni->ReleaseStringUTFChars(retString, chars);
	app->activity->vm->DetachCurrentThread();
	return ret;
}

void toggleIME(ANDROID_APP app, bool pShow, const char* hint) {
	if (!app || !app->activity || !app->activity->vm)
		return;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);

	jmethodID MethodIME = jni->GetMethodID(ClassNativeActivity, "toggleIME",
			"(Ljava/lang/String;Z)V");
	jstring hintstring = NULL;
	if (hint) {
		hintstring = jni->NewStringUTF(hint);
	}
	jni->CallVoidMethod(lNativeActivity, MethodIME, hintstring, pShow);
	if (hintstring) {
		jni->DeleteLocalRef(hintstring);
	}
	jni->DeleteLocalRef(ClassNativeActivity);
	app->activity->vm->DetachCurrentThread();
}

void toggleGlobalIME(ANDROID_APP app, bool pShow) {
	if (!app || !app->activity || !app->activity->vm)
		return;

	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	jint lFlags = 2;

	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);

	// Retrieves Context.INPUT_METHOD_SERVICE.
	jclass ClassContext = jni->FindClass("android/content/Context");
	jfieldID FieldINPUT_METHOD_SERVICE = jni->GetStaticFieldID(ClassContext,
			"INPUT_METHOD_SERVICE", "Ljava/lang/String;");
	jobject INPUT_METHOD_SERVICE = jni->GetStaticObjectField(ClassContext,
			FieldINPUT_METHOD_SERVICE);
	//jniCheck(INPUT_METHOD_SERVICE);

	// Runs getSystemService(Context.INPUT_METHOD_SERVICE).
	jclass ClassInputMethodManager = jni->FindClass(
			"android/view/inputmethod/InputMethodManager");
	jmethodID MethodGetSystemService = jni->GetMethodID(ClassNativeActivity,
			"getSystemService", "(Ljava/lang/String;)Ljava/lang/Object;");
	jobject lInputMethodManager = jni->CallObjectMethod(lNativeActivity,
			MethodGetSystemService, INPUT_METHOD_SERVICE);

	// Runs getWindow().getDecorView().
	jmethodID MethodGetWindow = jni->GetMethodID(ClassNativeActivity,
			"getWindow", "()Landroid/view/Window;");
	jobject lWindow = jni->CallObjectMethod(lNativeActivity, MethodGetWindow);
	jclass ClassWindow = jni->FindClass("android/view/Window");
	jmethodID MethodGetDecorView = jni->GetMethodID(ClassWindow, "getDecorView",
			"()Landroid/view/View;");
	jobject lDecorView = jni->CallObjectMethod(lWindow, MethodGetDecorView);

	if (pShow) {
		// Runs lInputMethodManager.showSoftInput(...).
		jmethodID MethodShowSoftInput = jni->GetMethodID(
				ClassInputMethodManager, "showSoftInput",
				"(Landroid/view/View;I)Z");
		jboolean lResult = jni->CallBooleanMethod(lInputMethodManager,
				MethodShowSoftInput, lDecorView, lFlags);
	} else {
		// Runs lWindow.getViewToken()
		jclass ClassView = jni->FindClass("android/view/View");
		jmethodID MethodGetWindowToken = jni->GetMethodID(ClassView,
				"getWindowToken", "()Landroid/os/IBinder;");
		jobject lBinder = jni->CallObjectMethod(lDecorView,
				MethodGetWindowToken);

		// lInputMethodManager.hideSoftInput(...).
		jmethodID MethodHideSoftInput = jni->GetMethodID(
				ClassInputMethodManager, "hideSoftInputFromWindow",
				"(Landroid/os/IBinder;I)Z");
		jboolean lRes = jni->CallBooleanMethod(lInputMethodManager,
				MethodHideSoftInput, lBinder, lFlags);
		jni->DeleteLocalRef(ClassView);
	}
	jni->DeleteLocalRef(ClassNativeActivity);
	jni->DeleteLocalRef(ClassContext);
	jni->DeleteLocalRef(ClassWindow);
	jni->DeleteLocalRef(ClassInputMethodManager);
	app->activity->vm->DetachCurrentThread();
}

void initJavaBridge(ANDROID_APP app, void* handle) {
	if (!app || !app->activity || !app->activity->vm)
		return;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodSetHandle = jni->GetMethodID(ClassNativeActivity,
			"setNativeHandle", "(I)V");
	jint code = (int) handle;
	jni->CallVoidMethod(lNativeActivity, MethodSetHandle, code);
	jni->DeleteLocalRef(ClassNativeActivity);
	app->activity->vm->DetachCurrentThread();
	return;
}

InitOptions* getInitOptions(ANDROID_APP app) {
	if (!app || !app->activity || !app->activity->vm)
		return NULL;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodSetHandle = jni->GetMethodID(ClassNativeActivity,
			"getNativeInitOptions", "()Ljava/nio/ByteBuffer;");
	jobject buffer = jni->CallObjectMethod(lNativeActivity, MethodSetHandle);
	void* data = jni->GetDirectBufferAddress(buffer);
	InitOptions* options = new InitOptions(data);
	jni->DeleteLocalRef(ClassNativeActivity);
	app->activity->vm->DetachCurrentThread();
	return options;
}

int getLocalAddr(ANDROID_APP app) {
	int addr = -1;
	if (!app || !app->activity || !app->activity->vm)
		return addr;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetAddr = jni->GetMethodID(ClassNativeActivity,
			"getLocalAddress", "()I");
	addr = jni->CallIntMethod(lNativeActivity, MethodGetAddr);
	jni->DeleteLocalRef(ClassNativeActivity);
	app->activity->vm->DetachCurrentThread();
	return addr;
}

bool isSoundEffectEnabled(ANDROID_APP app) {
	bool isEnabled = false;
	if (!app || !app->activity || !app->activity->vm)
		return isEnabled;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return true;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);
	jmethodID MethodCheckSE = jni->GetMethodID(classApp, "isSoundEffectEnabled",
			"()Z");
	jboolean result = jni->CallBooleanMethod(application, MethodCheckSE);
	if (result > 0) {
		isEnabled = true;
	} else {
		isEnabled = false;
	}
	jni->DeleteLocalRef(ClassNativeActivity);
	jni->DeleteLocalRef(classApp);
	app->activity->vm->DetachCurrentThread();
	return isEnabled;
}

void showAndroidComboBoxCompat(ANDROID_APP app, bool pShow, char** pContents,
		int count, int mode) {
	if (!app || !app->activity || !app->activity->vm)
		return;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodComboxBoxCompat = jni->GetMethodID(ClassNativeActivity,
			"showComboBoxCompat", "([Ljava/lang/String;ZI)V");
	jclass strClass = jni->FindClass("java/lang/String");
	jobjectArray array = jni->NewObjectArray(count, strClass, 0);
	jstring str;
	for (int i = 0; i < count; i++) {
		str = jni->NewStringUTF(*(pContents + i));
		jni->SetObjectArrayElement(array, i, str);
	}
	jni->CallVoidMethod(lNativeActivity, MethodComboxBoxCompat, array, pShow,
			mode);
	jni->DeleteLocalRef(ClassNativeActivity);
	app->activity->vm->DetachCurrentThread();

}

void toggleOverlayView(ANDROID_APP app, bool pShow) {
	if (!app || !app->activity || !app->activity->vm)
		return;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID overlayMethod = jni->GetMethodID(ClassNativeActivity,
			"toggleOverlayView", "(Z)V");
	jni->CallVoidMethod(lNativeActivity, overlayMethod, pShow);
	jni->DeleteLocalRef(ClassNativeActivity);
	app->activity->vm->DetachCurrentThread();
}

void process_input(ANDROID_APP app,
		struct android_poll_source* source) {
	AInputEvent* event = NULL;
	if (AInputQueue_getEvent(app->inputQueue, &event) >= 0) {
		int type = AInputEvent_getType(event);
		bool skip_predispatch = AInputEvent_getType(event)
				== AINPUT_EVENT_TYPE_KEY
				&& AKeyEvent_getKeyCode(event) == AKEYCODE_BACK;

		// skip predispatch (all it does is send to the IME)
		if (!skip_predispatch
				&& AInputQueue_preDispatchEvent(app->inputQueue, event)) {
			return;
		}

		int32_t handled = 0;
		if (app->onInputEvent != NULL)
			handled = app->onInputEvent(app, event);
		AInputQueue_finishEvent(app->inputQueue, event, handled);
	} else {
//        LOGE("Failure reading next input event: %s\n", strerror(errno));
	}
}

s32 handleInput(ANDROID_APP app, AInputEvent* androidEvent) {
	IrrlichtDevice* device = (IrrlichtDevice*) app->userData;
	s32 Status = 0;

	if (AInputEvent_getType(androidEvent) == AINPUT_EVENT_TYPE_MOTION) {
		SEvent Event;
		Event.EventType = EET_TOUCH_INPUT_EVENT;

		s32 EventAction = AMotionEvent_getAction(androidEvent);
		s32 EventType = EventAction & AMOTION_EVENT_ACTION_MASK;

		bool TouchReceived = true;

		switch (EventType) {
		case AMOTION_EVENT_ACTION_DOWN:
		case AMOTION_EVENT_ACTION_POINTER_DOWN:
			Event.TouchInput.Event = ETIE_PRESSED_DOWN;
			break;
		case AMOTION_EVENT_ACTION_MOVE:
			Event.TouchInput.Event = ETIE_MOVED;
			break;
		case AMOTION_EVENT_ACTION_UP:
		case AMOTION_EVENT_ACTION_POINTER_UP:
		case AMOTION_EVENT_ACTION_CANCEL:
			Event.TouchInput.Event = ETIE_LEFT_UP;
			break;
		default:
			TouchReceived = false;
			break;
		}

		if (TouchReceived) {
			// Process all touches for move action.
			if (Event.TouchInput.Event == ETIE_MOVED) {
				s32 PointerCount = AMotionEvent_getPointerCount(androidEvent);

				for (s32 i = 0; i < PointerCount; ++i) {
					Event.TouchInput.ID = AMotionEvent_getPointerId(
							androidEvent, i);
					Event.TouchInput.X = AMotionEvent_getX(androidEvent, i);
					Event.TouchInput.Y = AMotionEvent_getY(androidEvent, i);

					device->postEventFromUser(Event);
				}
			} else // Process one touch for other actions.
			{
				s32 PointerIndex = (EventAction
						& AMOTION_EVENT_ACTION_POINTER_INDEX_MASK)
						>> AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT;

				Event.TouchInput.ID = AMotionEvent_getPointerId(androidEvent,
						PointerIndex);
				Event.TouchInput.X = AMotionEvent_getX(androidEvent,
						PointerIndex);
				Event.TouchInput.Y = AMotionEvent_getY(androidEvent,
						PointerIndex);

				device->postEventFromUser(Event);
			}

			Status = 1;
		}
	} else if (AInputEvent_getType(androidEvent) == AINPUT_EVENT_TYPE_KEY) {
		s32 key = AKeyEvent_getKeyCode(androidEvent);
		if (key == AKEYCODE_BACK) {
			Status = 1;
		}
	}
	return Status;
}

bool android_deck_delete(const char* deck_name) {
	int status;
	std::string ext_deck_name;
	if (deck_name[0] != '/' && !(deck_name[0] == '.' && deck_name[1] == '/')) {
		ext_deck_name.append("./deck/").append(deck_name).append(".ydk");
	} else {
		ext_deck_name.append(deck_name);
	}
	status = remove(ext_deck_name.c_str());

	return status == 0;
}

void runWindbot(ANDROID_APP app, const char* args) {
	if (!app || !app->activity || !app->activity->vm)
		return;
	JNIEnv* jni = 0;
	app->activity->vm->AttachCurrentThread(&jni, NULL);
	if (!jni)
		return;
	// Retrieves NativeActivity.
	jobject lNativeActivity = app->activity->clazz;
	jclass ClassNativeActivity = jni->GetObjectClass(lNativeActivity);
	jmethodID MethodGetApp = jni->GetMethodID(ClassNativeActivity,
			"getApplication", "()Landroid/app/Application;");
	jobject application = jni->CallObjectMethod(lNativeActivity, MethodGetApp);
	jclass classApp = jni->GetObjectClass(application);
	jmethodID runWindbotMethod = jni->GetMethodID(classApp, "runWindbot",
			"(Ljava/lang/String;)V");
	jstring argsstring = jni->NewStringUTF(args);
	jni->CallVoidMethod(application, runWindbotMethod, argsstring);
	if (argsstring) {
		jni->DeleteLocalRef(argsstring);
	}
	jni->DeleteLocalRef(classApp);
	jni->DeleteLocalRef(ClassNativeActivity);
	app->activity->vm->DetachCurrentThread();
}

} // namespace android
} // namespace irr
