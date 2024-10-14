package searchengine.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@ControllerAdvice
public class ExceptionHandlerAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDescription> handler(Exception exception){
        log.error("Handle", exception);

        if(exception instanceof NotFoundException ex){
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorDescription(ex));
        }
        if(exception instanceof BadRequestException ex){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorDescription(ex));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorDescription(" --> Неизвестная ошибка"));
    }
}
