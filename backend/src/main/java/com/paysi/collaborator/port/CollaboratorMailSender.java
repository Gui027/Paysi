package com.paysi.collaborator.port;

public interface CollaboratorMailSender {
    void sendInvite(String destinationEmail, String ownerName);
}
