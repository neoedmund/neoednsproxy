package neoe.dns;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;

import neoe.dns.format.DNSMessage;
import neoe.util.Log;

public class AntiVirus {
	/** some virus send user data via DNS record, here clear those data */
	public static DNSMessage clearifyQuestion(DNSMessage msg) throws Exception {
		ByteArrayOutputStream ba;
		DataOutputStream out = new DataOutputStream(ba = new ByteArrayOutputStream());
		out.writeShort(msg.getId());
		out.writeShort(msg.getFlags());
		out.writeShort(msg.getQuestions().length);
		out.writeShort(msg.getAnswers().length);
		out.writeShort(msg.getNameServers().length);
		out.writeShort(msg.getAdditionalRecords().length);
		msg.getQuestions()[0].dump(out);
		out.close();
		return DNSMessage.parse(ByteBuffer.wrap(ba.toByteArray()));
	}

	public static boolean isBadQuestion(DNSMessage msg) {
		if (msg.isResponse()) {
			Log.log("[t]isBadQuestion.isResponse");
			return true;
		}
		if (msg.getAnswers().length != 0 || msg.getAdditionalRecords().length != 0 || msg.getNameServers().length != 0) {
			Log.log("[t]isBadQuestion.length=0");
			return true;
		}
		if (msg.getQuestions().length != 1) {
			Log.log("[t]isBadQuestion.getQuestions="+msg.getQuestions().length);
			return true;
		}
		return false;
	}

	public static String getSecurityString(DNSMessage msg) {
		return String.format("%s,cnt:%d,%d,%d,%d", msg.toIdString(), msg.getQuestions().length, msg.getAnswers().length, msg.getNameServers().length, msg.getAdditionalRecords().length);
	}
}
