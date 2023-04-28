package tregression.empiricalstudy.config;

import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;

public class ConsoleTransferListener extends AbstractTransferListener {

    private void log(String prefix, TransferResource resource) {
        System.out.println(prefix + ": " + resource.getRepositoryUrl() + resource.getResourceName());
    }

    @Override
    public void transferInitiated(TransferEvent event) {
        String message = event.getRequestType() == TransferEvent.RequestType.PUT ? "Uploading" : "Downloading";
        log(message, event.getResource());
    }

    @Override
    public void transferSucceeded(TransferEvent event) {
        String message = event.getRequestType() == TransferEvent.RequestType.PUT ? "Uploaded" : "Downloaded";
        log(message, event.getResource());
    }

    @Override
    public void transferFailed(TransferEvent event) {
        String message = event.getRequestType() == TransferEvent.RequestType.PUT ? "Failed to upload" : "Failed to download";
        log(message, event.getResource());
    }
}

