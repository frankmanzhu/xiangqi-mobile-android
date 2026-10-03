// JNI surface over PikafishBridge (shared verbatim with the iOS app).
#include <jni.h>

#include <string>
#include <vector>

#include "PikafishBridge.h"

namespace {

void throw_java(JNIEnv *env, const char *message) {
    jclass cls = env->FindClass("com/frankzhu/xiangqi/engine/NativeEngineException");
    if (cls != nullptr)
        env->ThrowNew(cls, message);
}

// Copies a Java String[] into owned std::strings plus a const char* view.
struct MoveList {
    std::vector<std::string> storage;
    std::vector<const char *> pointers;

    MoveList(JNIEnv *env, jobjectArray array) {
        const jsize count = array == nullptr ? 0 : env->GetArrayLength(array);
        storage.reserve(count);
        for (jsize i = 0; i < count; ++i) {
            auto *item = static_cast<jstring>(env->GetObjectArrayElement(array, i));
            const char *chars = env->GetStringUTFChars(item, nullptr);
            storage.emplace_back(chars);
            env->ReleaseStringUTFChars(item, chars);
            env->DeleteLocalRef(item);
        }
        for (const auto &move : storage)
            pointers.push_back(move.c_str());
    }
};

std::string to_std(JNIEnv *env, jstring value) {
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

}  // namespace

extern "C" {

JNIEXPORT jintArray JNICALL
Java_com_frankzhu_xiangqi_engine_NativePikafish_nativeRulesResult(JNIEnv *env, jobject, jstring fen,
                                                                  jobjectArray moves) {
    const std::string fenText = to_std(env, fen);
    MoveList list(env, moves);
    PFRuleResult result{};
    PFEngineError error{};
    if (!pf_rules_result(fenText.c_str(), list.pointers.data(), list.pointers.size(), &result, &error)) {
        throw_java(env, error.message);
        return nullptr;
    }
    jintArray out = env->NewIntArray(2);
    const jint values[2] = {result.outcome, result.reason};
    env->SetIntArrayRegion(out, 0, 2, values);
    return out;
}

JNIEXPORT jlong JNICALL
Java_com_frankzhu_xiangqi_engine_NativePikafish_nativeCreate(JNIEnv *env, jobject, jstring networkPath) {
    const std::string path = to_std(env, networkPath);
    PFEngineError error{};
    PFPikafishSession *session = pf_engine_create(path.c_str(), &error);
    if (session == nullptr) {
        throw_java(env, error.message);
        return 0;
    }
    return reinterpret_cast<jlong>(session);
}

JNIEXPORT void JNICALL
Java_com_frankzhu_xiangqi_engine_NativePikafish_nativeDestroy(JNIEnv *, jobject, jlong handle) {
    pf_engine_destroy(reinterpret_cast<PFPikafishSession *>(handle));
}

JNIEXPORT void JNICALL
Java_com_frankzhu_xiangqi_engine_NativePikafish_nativeSetPosition(JNIEnv *env, jobject, jlong handle,
                                                                  jstring fen, jobjectArray moves) {
    const std::string fenText = to_std(env, fen);
    MoveList list(env, moves);
    PFEngineError error{};
    if (!pf_engine_set_position(reinterpret_cast<PFPikafishSession *>(handle), fenText.c_str(),
                                list.pointers.data(), list.pointers.size(), &error))
        throw_java(env, error.message);
}

JNIEXPORT jstring JNICALL
Java_com_frankzhu_xiangqi_engine_NativePikafish_nativeBestMove(JNIEnv *env, jobject, jlong handle,
                                                               jint moveTimeMs) {
    char output[6] = {0};
    PFEngineError error{};
    if (!pf_engine_best_move(reinterpret_cast<PFPikafishSession *>(handle), moveTimeMs, 0, 0, output,
                             &error)) {
        throw_java(env, error.message);
        return nullptr;
    }
    return env->NewStringUTF(output);
}

JNIEXPORT void JNICALL
Java_com_frankzhu_xiangqi_engine_NativePikafish_nativeStop(JNIEnv *, jobject, jlong handle) {
    pf_engine_stop(reinterpret_cast<PFPikafishSession *>(handle));
}

JNIEXPORT jstring JNICALL
Java_com_frankzhu_xiangqi_engine_NativePikafish_nativeRevision(JNIEnv *env, jobject) {
    return env->NewStringUTF(pf_engine_revision());
}

}  // extern "C"
