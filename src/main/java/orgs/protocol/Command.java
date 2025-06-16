package orgs.protocol;


public enum Command {
    // User Management
    LOGIN,
    REGISTER, // Create User (C)
    GET_USER_PROFILE, // Read User (R)
    UPDATE_USER_PROFILE, // Update User (U)
    DELETE_USER, // Delete User (D)
    GET_ALL_USERS, // Read all users (Admin/Contact list scenario)

    // Chat Management
    CREATE_CHAT, // Create Chat (C)
    GET_USER_CHATS, // Read Chats (R) - Chats a user is part of
    GET_CHAT_DETAILS, // Read Chat Details (R)
    UPDATE_CHAT, // Update Chat (U) - Name, type
    DELETE_CHAT, // Delete Chat (D)

    // Message Management
    SEND_MESSAGE, // Create Message (C)
    GET_CHAT_MESSAGES, // Read Messages (R)
    UPDATE_MESSAGE, // Update Message (U)
    DELETE_MESSAGE, // Delete Message (D)
    MARK_MESSAGE_AS_READ, // Update view_count (U)

    // Chat Participant Management
    ADD_CHAT_PARTICIPANT, // Create Chat Participant (C)
    GET_CHAT_PARTICIPANTS, // Read Chat Participants (R)
    UPDATE_CHAT_PARTICIPANT, // Update Chat Participant (U) - Role
    REMOVE_CHAT_PARTICIPANT, // Delete Chat Participant (D)

    // Contact Management (These would interact with the contact table in your DB)
    ADD_CONTACT, // C
    GET_CONTACTS, // R
    REMOVE_CONTACT, // D (Update is less common here)
    BLOCK_USER, // C/U (Blocks/unblocks a user)
    UNBLOCK_USER,

    // Notification Management
    CREATE_NOTIFICATION, // C (Server might create these)
    GET_USER_NOTIFICATIONS, // R
    MARK_NOTIFICATION_AS_READ, // U
    DELETE_NOTIFICATION, // D

    // Generic Server Response
    SERVER_RESPONSE, // Generic success/failure response
    ERROR // For server-side errors
}