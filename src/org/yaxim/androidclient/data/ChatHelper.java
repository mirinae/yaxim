package org.yaxim.androidclient.data;

import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.chat.ChatWindow;
import org.yaxim.androidclient.chat.MUCChatWindow;
import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.data.RosterProvider.RosterConstants;
import org.yaxim.androidclient.dialogs.AddRosterItemDialog;
import org.yaxim.androidclient.dialogs.ConfirmDialog;
import org.yaxim.androidclient.dialogs.EditMUCDialog;
import org.yaxim.androidclient.dialogs.GroupNameView;
import org.yaxim.androidclient.preferences.NotificationPrefs;
import org.yaxim.androidclient.service.IXMPPChatService;
import org.yaxim.androidclient.util.StatusMode;
import org.yaxim.androidclient.util.XMPPHelper;
import org.yaxim.androidclient.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

public class ChatHelper {

	public static void markAllAsRead(Context ctx) {
		ContentValues cv = new ContentValues();
		cv.put(ChatConstants.DELIVERY_STATUS, ChatConstants.DS_SENT_OR_READ);
		ctx.getContentResolver().update(ChatProvider.CONTENT_URI, cv,
						ChatConstants.DIRECTION + " = " + ChatConstants.INCOMING + " AND "
						+ ChatConstants.DELIVERY_STATUS + " = " + ChatConstants.DS_NEW, null);
	}
	
	public static void markAsRead(Context ctx, String jid) {
		ContentValues cv = new ContentValues();
		cv.put(ChatConstants.DELIVERY_STATUS, ChatConstants.DS_SENT_OR_READ);
		ctx.getContentResolver().update(ChatProvider.CONTENT_URI, cv,
				ChatProvider.ChatConstants.JID + " = ? AND "
						+ ChatConstants.DIRECTION + " = " + ChatConstants.INCOMING + " AND "
						+ ChatConstants.DELIVERY_STATUS + " = " + ChatConstants.DS_NEW,
				new String[]{jid});
	}

	public static void clearAndRespond(Context ctx, BroadcastReceiver br, String jid, String response) {
		// mark message(s) as read
		markAsRead(ctx, jid);

		// obtain service reference if possible
		Intent serviceIntent = new Intent(ctx, org.yaxim.androidclient.service.XMPPService.class);
		serviceIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");
		IXMPPChatService.Stub cs = (IXMPPChatService.Stub)br.peekService(ctx, serviceIntent);
		if (cs == null) {
			android.util.Log.d("ChatHelper", "Could not peek Service for " + jid);
			return;
		}
		try {
			cs.clearNotifications(jid);
			if (response != null && response.length() > 0)
				cs.sendMessage(jid, response, null, -1);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public static void sendMessage(final Context ctx, final String jid, final String message) {
		Intent serviceIntent = new Intent(ctx, org.yaxim.androidclient.service.XMPPService.class);
		serviceIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");
		ServiceConnection c = new ServiceConnection() {
			public void onServiceConnected(ComponentName name, IBinder service) {
				IXMPPChatService chatService = IXMPPChatService.Stub.asInterface(service);
				try {
					if (message != null)
						chatService.sendMessage(jid, message, null, -1);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				ctx.unbindService(this);
			}
			public void onServiceDisconnected(ComponentName name) {}
		};
		ctx.bindService(serviceIntent, c, Context.BIND_AUTO_CREATE);
	}

	public static void removeChatHistory(Context ctx, String jid) {
		// TODO: MUC PM history
		ctx.getContentResolver().delete(ChatProvider.CONTENT_URI,
				ChatProvider.ChatConstants.JID + " = ?", new String[] { jid });
	}

	public static void startChatActivity(Context ctx, String user, String userName, String message, Uri image) {
		Intent chatIntent = new Intent(ctx, ChatWindow.class);
		if (ChatRoomHelper.isRoom(ctx, user))
			chatIntent.setClass(ctx, MUCChatWindow.class);
		Uri userNameUri = Uri.parse(user);
		chatIntent.setData(userNameUri);
		chatIntent.putExtra(ChatWindow.INTENT_EXTRA_USERNAME, userName);
		if (message != null) {
			chatIntent.putExtra(ChatWindow.INTENT_EXTRA_MESSAGE, message);
		}
		if (image != null) {
			chatIntent.putExtra(Intent.EXTRA_STREAM, image);
			chatIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		}
		ctx.startActivity(chatIntent);
	}
	public static void startChatActivity(Context ctx, String user, String userName, String message) {
		startChatActivity(ctx, user, userName, message, null);
	}

	public static Bitmap generateQr(String value, int size) {
		QRCodeWriter qr = new QRCodeWriter();
		final Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
		hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
		try {
			BitMatrix matrix = qr.encode(value, BarcodeFormat.QR_CODE, size, size, hints);
			int final_width = matrix.getWidth();
			int final_height = matrix.getHeight();
			int[] pixels = new int[final_width * final_height];
			for (int y = 0; y < final_height; y++)
				for (int x = 0; x < final_width; x++)
					pixels[x + final_width*y] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
			Bitmap bmp = Bitmap.createBitmap(final_width, final_height, Bitmap.Config.ARGB_8888);
			bmp.setPixels(pixels, 0, final_width, 0, 0, final_width, final_height);
			return bmp;
		} catch (WriterException e) {
			e.printStackTrace();
			return null;
		}
	}
	public static void showQrDialog(final Activity act, final String jid, final String link,
			final String userName) {
		LayoutInflater inflater = (LayoutInflater) act.getSystemService(act.LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.qrcode_dialog,
				(ViewGroup) act.findViewById(R.id.layout_root));

		int x = act.getWindowManager().getDefaultDisplay().getWidth();
		int y = act.getWindowManager().getDefaultDisplay().getHeight();
		int width = (x < y ? x : y) * 4 / 5;

		View.OnClickListener l = new View.OnClickListener() {
			public void onClick(View v) {
				XMPPHelper.shareLink(act, R.string.roster_contextmenu_contact_share,
						link);
			}
		};
		TextView messageView = (TextView) layout.findViewById(R.id.text);
		messageView.setOnClickListener(l);
		messageView.setText(jid);

		ImageView qrCode = (ImageView) layout.findViewById(R.id.qr_code);
		qrCode.setImageBitmap(generateQr(link, width));
		qrCode.setOnClickListener(l);
		new AlertDialog.Builder(act)
				.setTitle(userName)
				.setView(layout)
				.setPositiveButton(R.string.roster_contextmenu_contact_share,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								XMPPHelper.shareLink(act, R.string.roster_contextmenu_contact_share,
										link);
							}
						})
				.create().show();
	}

	public static void removeChatHistoryDialog(final Context ctx, final String jid, final String userName) {
		new AlertDialog.Builder(ctx)
			.setTitle(R.string.deleteChatHistory_title)
			.setMessage(ctx.getString(R.string.deleteChatHistory_text, userName, jid))
			.setPositiveButton(android.R.string.yes,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							removeChatHistory(ctx, jid);
						}
					})
			.setNegativeButton(android.R.string.no, null)
			.create().show();
	}

	public interface EditOk {
		abstract public void ok(String result);
	}

	public static void editTextDialog(Activity act, int titleId, CharSequence message, String text,
						final EditOk ok) {
		LayoutInflater inflater = (LayoutInflater) act.getSystemService(act.LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.edittext_dialog,
				(ViewGroup) act.findViewById(R.id.layout_root));

		TextView messageView = (TextView) layout.findViewById(R.id.text);
		messageView.setText(message);
		messageView.setVisibility(TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
		final EditText input = (EditText) layout.findViewById(R.id.editText);
		input.setTransformationMethod(android.text.method.SingleLineTransformationMethod.getInstance());
		input.setText(text);
		new AlertDialog.Builder(act)
				.setTitle(titleId)
				.setView(layout)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						String newName = input.getText().toString();
						if (newName.length() != 0)
							ok.ok(newName);
					}})
				.setNegativeButton(android.R.string.cancel, null)
				.create().show();
	}

	public static void removeRosterItemDialog(final Activity act, final String jid, final String userName) {
		new AlertDialog.Builder(act)
				.setTitle(R.string.deleteRosterItem_title)
				.setMessage(act.getString(R.string.deleteRosterItem_text, userName, jid))
				.setPositiveButton(android.R.string.yes,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								try {
									YaximApplication.getApp(act).getSmackable().removeRosterItem(jid);
									if (act instanceof ChatWindow)
										act.finish();
								} catch (Exception e) {
									shortToastNotify(act, e);
								}
							}
						})
				.setNegativeButton(android.R.string.no, null)
				.create().show();
	}
	public static void renameRosterItemDialog(final Activity act, final String jid, final String userName) {
		String newUserName = userName;
		if (jid.equals(userName))
			newUserName = XMPPHelper.capitalizeString(jid.split("@")[0]);
		editTextDialog(act, R.string.RenameEntry_title,
				act.getString(R.string.RenameEntry_summ, userName, jid),
				newUserName, new EditOk() {
					public void ok(String result) {
						try {
							YaximApplication.getApp(act).getSmackable().renameRosterItem(jid, result);
						} catch (Exception e) {
							shortToastNotify(act, e);
						}
					}
				});
	}
	public static void moveRosterItemToGroupDialog(final Activity act, final String jabberID) {
		LayoutInflater inflater = (LayoutInflater)act.getSystemService(
				act.LAYOUT_INFLATER_SERVICE);
		View group = inflater.inflate(R.layout.moverosterentrytogroupview, null, false);
		final GroupNameView gv = (GroupNameView)group.findViewById(R.id.moverosterentrytogroupview_gv);
		gv.setGroupList(getRosterGroups(act));
		new AlertDialog.Builder(act)
				.setTitle(R.string.MoveRosterEntryToGroupDialog_title)
				.setView(group)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								try {
									YaximApplication.getApp(act).getSmackable().moveRosterItemToGroup(jabberID,
											gv.getGroupName());
								} catch (Exception e) {
									shortToastNotify(act, e);
								}
							}
						})
				.setNegativeButton(android.R.string.cancel, null)
				.create().show();
	}



	public static boolean handleJidOptions(Activity act, int menu_id, String jid, String userName) {
		Intent ringToneIntent = new Intent(act, NotificationPrefs.class);
		switch (menu_id) {
		// generic options (roster_item_contextmenu.xml)
		case R.id.roster_contextmenu_contact_mark_as_read:
			markAsRead(act, jid);
			return true;
		case R.id.roster_contextmenu_contact_delmsg:
			removeChatHistoryDialog(act, jid, userName);
			return true;

		// contact specific options (contact_options.xml)
		case R.id.roster_contextmenu_contact_delete:
			removeRosterItemDialog(act, jid, userName);
			return true;
		case R.id.roster_contextmenu_contact_rename:
			renameRosterItemDialog(act, jid, userName);
			return true;
		case R.id.roster_contextmenu_contact_request_auth:
			YaximApplication.getApp(act).getSmackable().sendPresenceRequest(jid, "subscribe");
			return true;
		case R.id.roster_contextmenu_contact_change_group:
			moveRosterItemToGroupDialog(act, jid);
			return true;

		case R.id.menu_add_friend:
			new AddRosterItemDialog(act, jid).show();
			return true;

		case R.id.roster_contextmenu_contact_share:
			showQrDialog(act, jid, XMPPHelper.createRosterLinkHTTPS(jid), userName);
			return true;

		// MUC specific options (muc_options.xml)
		case R.id.roster_contextmenu_muc_edit:
			new EditMUCDialog(act, jid).dontOpen().show();
			return true;
		case R.id.roster_contextmenu_muc_leave:
			ConfirmDialog.showMucLeave(act, jid);
			return true;
		case R.id.roster_contextmenu_muc_ringtone:
			ringToneIntent.setData(Uri.parse("muc"));
		case R.id.roster_contextmenu_ringtone:
			ringToneIntent.putExtra("jid", jid);
			ringToneIntent.putExtra("name", userName);
			act.startActivity(ringToneIntent);
			return true;
		case R.id.roster_contextmenu_muc_share:
			showQrDialog(act, jid, XMPPHelper.createMucLinkHTTPS(jid), userName);
			return true;
		default:
			return false;
		}
	}

	public static void shortToastNotify(Context ctx, String msg) {
		Toast toast = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT);
		toast.show();
	}
	public static void shortToastNotify(Context ctx, Throwable e) {
		e.printStackTrace();
		while (e.getCause() != null)
			e = e.getCause();
		shortToastNotify(ctx,e.getMessage());
	}

	private static final String[] ROSTER_QUERY = new String[] {
		RosterConstants.JID,
		RosterConstants.ALIAS,
	};
	public static final int ROSTER_FILTER_ALL = 0;
	public static final int ROSTER_FILTER_CONTACTS = 1;
	public static final int ROSTER_FILTER_MUCS = 2;
	public static final int ROSTER_FILTER_SUBSCRIPTIONS = 3;
	public static List<String[]> getRosterContacts(Context ctx, int filter) {
		// we want all, online and offline
		List<String[]> list = new ArrayList<String[]>();
		String selection = null;
		if (filter == ROSTER_FILTER_CONTACTS)
			selection = "roster_group != '" + RosterConstants.MUCS + "'";
		else if (filter == ROSTER_FILTER_MUCS)
			selection = "roster_group == '" + RosterConstants.MUCS + "'";
		else if (filter == ROSTER_FILTER_SUBSCRIPTIONS)
			selection = "status_mode == " + StatusMode.subscribe.ordinal();
		Cursor cursor = ctx.getContentResolver().query(RosterProvider.CONTENT_URI, ROSTER_QUERY,
					selection, null, RosterConstants.ALIAS);
		int JIDIdx = cursor.getColumnIndex(RosterConstants.JID);
		int aliasIdx = cursor.getColumnIndex(RosterConstants.ALIAS);
		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			String jid = cursor.getString(JIDIdx);
			String alias = cursor.getString(aliasIdx);
			if ((alias == null) || (alias.length() == 0)) alias = jid;
			list.add(new String[] { jid, alias });
			cursor.moveToNext();
		}
		cursor.close();
		return list;
	}

	private static final String[] GROUPS_QUERY = new String[] {
			RosterConstants._ID,
			RosterConstants.GROUP,
	};
	public static List<String> getRosterGroups(Context ctx) {
		// we want all, online and offline
		List<String> list = new ArrayList<String>();
		Cursor cursor = ctx.getContentResolver().query(RosterProvider.GROUPS_URI, GROUPS_QUERY,
				null, null, RosterConstants.GROUP);
		int idx = cursor.getColumnIndex(RosterConstants.GROUP);
		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			list.add(cursor.getString(idx));
			cursor.moveToNext();
		}
		cursor.close();
		list.remove(RosterProvider.RosterConstants.MUCS);
		return list;
	}


	public static Collection<String> getXMPPDomains(Context ctx, int filter) {
		HashSet<String> servers = new HashSet<String>();
		for (String[] c : getRosterContacts(ctx, filter)) {
			String[] jid_split = c[0].split("@", 2);
			if (jid_split.length > 1)
				servers.add(jid_split[1]);
		}
		return servers;
	}
}
