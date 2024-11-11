/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Controller;


import Dao.RoomBookingDAO;
import Dao.RoomDaoHoai;
import Dao.StudentDao;
import Model.RoomByHoai;
import Model.RoomBooking;
import Model.Student;
import com.google.gson.JsonObject;
import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import Utils.Config;
import com.google.gson.Gson;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 *
 * @author Admin
 */
@WebServlet(name = "PaymentController", urlPatterns = {"/payment"})
public class PaymentController extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse respone) throws ServletException, IOException {
        StudentDao d = new StudentDao();
        HttpSession session = request.getSession();
        Student student = (Student) session.getAttribute("data");
        if (student == null) {
            request.getRequestDispatcher("Login.jsp").forward(request, respone);
        }
        String user = student.getId();
      
        Config.vnp_Returnurl = Config.vnp_Returnurl.replace("$PORT", request.getServerPort()+"");
        
        String amount = request.getParameter("amount");
        String room = request.getParameter("dom");
        
        RoomByHoai roomObj = new RoomDaoHoai().getRoomById(room);
        if (roomObj.getRoom_usage() <= 0) {
            respone.sendRedirect("booking?fm=Room not available&l&pid=" + room);
            return;
        }  
        
        List<RoomBooking> roomBookedList = new RoomBookingDAO().getBookingsByUserId(user);
        if (roomBookedList.size() > 0) {
            respone.sendRedirect("booking?fm=Cant book more room&l&pid=" + room);
            return;
        }  
        
        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String vnp_OrderInfo = "pay pay";
        String orderType = "other";
        String vnp_TxnRef = Config.getRandomNumber(8);
        String vnp_IpAddr = Config.getIpAddress(request);
        String vnp_TmnCode = Config.vnp_TmnCode;
        Map vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version); //Phiên bản cũ là 2.0.0, 2.0.1 thay đổi sang 2.1.0
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amount + "00"));
        vnp_Params.put("vnp_CurrCode", "VND");
        String bank_code = request.getParameter("bankcode");
        if (bank_code != null && !bank_code.isEmpty()) {
            vnp_Params.put("vnp_BankCode", bank_code);
        }
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", vnp_OrderInfo);
        vnp_Params.put("vnp_OrderType", orderType);

        String locate = request.getParameter("language");
        if (locate != null && !locate.isEmpty()) {
            vnp_Params.put("vnp_Locale", locate);
        } else {
            vnp_Params.put("vnp_Locale", "vn");
        }
        vnp_Params.put("vnp_ReturnUrl", Config.vnp_Returnurl);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));

        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());

        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        //Build data to hash and querystring
        List fieldNames = new ArrayList(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator itr = fieldNames.iterator();

        while (itr.hasNext()) {
            String fieldName = (String) itr.next();
            String fieldValue = (String) vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                //Build hash data
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                //Build query
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                query.append('=');
                query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }
        String queryUrl = query.toString();
        String vnp_SecureHash = Config.hmacSHA512(Config.vnp_HashSecret, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
        String paymentUrl = Config.vnp_PayUrl + "?" + queryUrl;

        //Section : Add new payment
        //Get user payment
        
        
        int month = Integer.parseInt(request.getParameter("month"));
        String note = request.getParameter("note");
        
        LocalDate checkinDate = LocalDate.now();
        LocalDate checkoutDate = checkinDate.plus(month, ChronoUnit.MONTHS);
        
        RoomBooking booking = new RoomBooking();
        booking.setRoomId(room);
        booking.setUsersId(user);
        booking.setCheckinDate(checkinDate);
        booking.setCheckoutDate(checkoutDate);
        booking.setNote(note);
        booking.setTotalPrice(BigDecimal.valueOf(Double.parseDouble(amount)));
        
        Config.booking = booking;

        respone.sendRedirect(paymentUrl);
    }

}
