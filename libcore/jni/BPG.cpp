#include <jni.h>
#include <malloc.h>

extern "C" {
#include "libbpg.h"

JNIEXPORT jbyteArray JNICALL Java_cn_garymb_ygomobile_core_BpgImage_nativeBpgImage(
        JNIEnv *env, jclass, jbyteArray imgdata) {
    jsize len = env->GetArrayLength(imgdata);
    jbyte *jarr = (jbyte *) malloc(len * sizeof(jbyte));
    env->GetByteArrayRegion(imgdata, 0, len, jarr);
    uint8_t *data = (uint8_t *) jarr;//uint8_t 就是byte
    BPGDecoderContext *img;
    BPGImageInfo img_info_s, *img_info = &img_info_s;
    int w, h, i, y, size, bufferIncrement, rowspan;
    //加载文件
    img = bpg_decoder_open();
    if (bpg_decoder_decode(img, data, (int) len) < 0) {
        return 0;
    }
    //解析信息
    bpg_decoder_get_info(img, img_info);
    w = img_info->width;
    h = img_info->height;
    size = w * y;
    //*rgb_line的长度 rgb=3,argb=4
    if (img_info->format == BPG_FORMAT_GRAY)
        rowspan = 1 * w;
    else
        rowspan = 3 * w;
    unsigned char *rgb_line = new unsigned char[rowspan];
    //输出到output
    unsigned int outBufLen = rowspan * h + 8;
    unsigned char *output = new unsigned char[outBufLen];
    output[0] = (unsigned char) (w & 0xFF);
    output[1] = (unsigned char) ((w & 0xFF00) >> 8);
    output[2] = (unsigned char) ((w & 0xFF0000) >> 16);
    output[3] = (unsigned char) ((w >> 24) & 0xFF);
    output[4] = (unsigned char) (h & 0xFF);
    output[5] = (unsigned char) ((h & 0xFF00) >> 8);
    output[6] = (unsigned char) ((h & 0xFF0000) >> 16);
    output[7] = (unsigned char) ((h >> 24) & 0xFF);
    bpg_decoder_start(img, BPG_OUTPUT_FORMAT_RGB24);
    bufferIncrement = 8;
    for (y = 0; y < h; y++) {
        bpg_decoder_get_line(img, rgb_line);
        for (i = 0; i < rowspan; i++) {
            output[bufferIncrement + i] = rgb_line[i];
        }
        bufferIncrement += rowspan;
    }
    bpg_decoder_close(img);
    delete[] rgb_line;
    jbyteArray jbarray = env->NewByteArray(outBufLen);
    jbyte *jy = (jbyte *) output;  //BYTE强制转换成Jbyte；
    env->SetByteArrayRegion(jbarray, 0, outBufLen, jy);            //将Jbyte 转换为jbarray数组
    env->DeleteLocalRef(imgdata);
    free(output);
    return jbarray;
}
};