package org.yaxim.androidclient.service;

import org.yaxim.androidclient.service.ParcelablePresence;

interface IXMPPMucService {
	void syncDbRooms();
	boolean inviteToRoom(String contactJid, String roomJid);
	String getMyMucNick(String jid);
	List<ParcelablePresence> getUserList(String jid);

	// TODO: private chat in a room
	//RoomInfo getRoomInfo(String room); TODO: make RoomInfo "parcelable"??
	// TODO: manage roles
	// TODO: manage subjects
	// TODO: manage affiliations
}
