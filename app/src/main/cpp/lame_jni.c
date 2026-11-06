#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include "lame/include/lame.h"

/* Returns a native handle (0 on failure). */
JNIEXPORT jlong JNICALL
Java_com_wavebox_app_LameNative_init(JNIEnv *env, jclass clazz,
                                     jint sampleRate, jint channels, jint bitrateKbps, jint quality) {
    (void) env; (void) clazz;
    lame_t lame = lame_init();
    if (!lame) return 0;
    lame_set_in_samplerate(lame, sampleRate);
    lame_set_out_samplerate(lame, sampleRate);
    lame_set_num_channels(lame, channels);
    lame_set_mode(lame, channels == 1 ? MONO : JOINT_STEREO);
    lame_set_brate(lame, bitrateKbps);
    lame_set_VBR(lame, vbr_off);          /* constant bitrate */
    lame_set_quality(lame, quality);      /* 0 best/slow ... 9 worst/fast; 5 = default, 7 = fast */
    lame_set_write_id3tag_automatic(lame, 0); /* the app writes its own ID3 tag */
    if (lame_init_params(lame) < 0) {
        lame_close(lame);
        return 0;
    }
    return (jlong) (intptr_t) lame;
}

/* pcm = interleaved 16-bit samples; frames = samples per channel. Returns bytes written or <0 on error. */
JNIEXPORT jint JNICALL
Java_com_wavebox_app_LameNative_encode(JNIEnv *env, jclass clazz, jlong handle,
                                       jshortArray pcm, jint frames, jint channels,
                                       jbyteArray out) {
    (void) clazz;
    lame_t lame = (lame_t) (intptr_t) handle;
    if (!lame) return -100;
    /* do all other JNI calls and allocation BEFORE entering the critical section */
    jsize outCap = (*env)->GetArrayLength(env, out);
    unsigned char *tmp = (unsigned char *) malloc((size_t) outCap);
    if (!tmp) return -102;
    jshort *in = (*env)->GetPrimitiveArrayCritical(env, pcm, NULL);
    if (!in) { free(tmp); return -101; }
    int n;
    if (channels == 1) {
        n = lame_encode_buffer(lame, in, NULL, frames, tmp, outCap);
    } else {
        n = lame_encode_buffer_interleaved(lame, in, frames, tmp, outCap);
    }
    (*env)->ReleasePrimitiveArrayCritical(env, pcm, in, JNI_ABORT);
    if (n > 0) (*env)->SetByteArrayRegion(env, out, 0, n, (const jbyte *) tmp);
    free(tmp);
    return n;
}

JNIEXPORT jint JNICALL
Java_com_wavebox_app_LameNative_flush(JNIEnv *env, jclass clazz, jlong handle, jbyteArray out) {
    (void) clazz;
    lame_t lame = (lame_t) (intptr_t) handle;
    if (!lame) return -100;
    jsize outCap = (*env)->GetArrayLength(env, out);
    unsigned char *tmp = (unsigned char *) malloc((size_t) outCap);
    if (!tmp) return -102;
    int n = lame_encode_flush(lame, tmp, outCap);
    if (n > 0) (*env)->SetByteArrayRegion(env, out, 0, n, (const jbyte *) tmp);
    free(tmp);
    return n;
}

JNIEXPORT void JNICALL
Java_com_wavebox_app_LameNative_close(JNIEnv *env, jclass clazz, jlong handle) {
    (void) env; (void) clazz;
    lame_t lame = (lame_t) (intptr_t) handle;
    if (lame) lame_close(lame);
}
