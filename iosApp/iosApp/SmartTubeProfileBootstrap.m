#import <Foundation/Foundation.h>

extern void NuvioInstallSmartTubeProfileOverlay(void);

__attribute__((constructor))
static void NuvioBootstrapSmartTubeProfile(void) {
    dispatch_async(dispatch_get_main_queue(), ^{
        NuvioInstallSmartTubeProfileOverlay();
    });
}
