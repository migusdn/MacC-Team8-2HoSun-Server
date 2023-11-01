package com.twohoseon.app.service.post;

import com.twohoseon.app.dto.request.CommentCreateRequestDTO;
import com.twohoseon.app.dto.response.PostCommentInfoDTO;
import com.twohoseon.app.entity.member.Member;
import com.twohoseon.app.entity.post.Post;
import com.twohoseon.app.entity.post.PostComment;
import com.twohoseon.app.exception.CommentNotFoundException;
import com.twohoseon.app.exception.PermissionDeniedException;
import com.twohoseon.app.exception.PostNotFoundException;
import com.twohoseon.app.repository.member.MemberRepository;
import com.twohoseon.app.repository.post.PostCommentRepository;
import com.twohoseon.app.repository.post.PostRepository;
import com.twohoseon.app.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.webjars.NotFoundException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @author : yongjukim
 * @version : 1.0.0
 * @package : twohoseon
 * @name : PostCommentServiceImpl
 * @date : 2023/10/18
 * @modifyed : $
 **/

@Slf4j
@Service
@RequiredArgsConstructor
public class PostCommentServiceImpl implements PostCommentService {

    private final PostCommentRepository postCommentRepository;
    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final NotificationService notificationService;

    @Override
//    @Transactional
    public void createComment(CommentCreateRequestDTO commentCreateRequestDTO) {
        Member member = getMemberFromRequest();

        Post post = postRepository.findById(commentCreateRequestDTO.getPostId())
                .orElseThrow(() -> new PostNotFoundException());
        boolean isSubComment = commentCreateRequestDTO.getParentId() != null;
        PostComment postComment = PostComment
                .builder()
                .author(member)
                .post(post)
                .content(commentCreateRequestDTO.getContent())
                .build();

        if (isSubComment) {
            PostComment parentPostComment = postCommentRepository.findById(commentCreateRequestDTO.getParentId())
                    .orElseThrow(() -> new CommentNotFoundException());

            if (parentPostComment.getPost() != post) {
                throw new NotFoundException("Not equal id");
            }
            postComment.updateParent(parentPostComment);
            parentPostComment.addChildComment(postComment);
        }

        post.addComment();
        CompletableFuture.runAsync(() -> {
            try {
                notificationService.sendPostCommentNotification(post, member.getUserNickname(), isSubComment);
            } catch (ExecutionException e) {
                log.debug("sendPostCommentNotification error: ", e);
            } catch (InterruptedException e) {
                log.debug("sendPostCommentNotification error: ", e);
            }
        });
    }

    @Override
    @Transactional
    public void deleteComment(Long postCommentId) {
        //TODO 유저 권한 체크
        //TODO 삭제시 자식이 존재하는 경우 자식들도 사라져야함.
        PostComment postComment = postCommentRepository.findById(postCommentId)
                .orElseThrow(() -> new CommentNotFoundException());

        if (postComment.getAuthor() != getMemberFromRequest()) {
            throw new PermissionDeniedException();
        }
        postComment.getPost().deleteComment();
        postCommentRepository.delete(postComment);
    }

    @Override
    @Transactional
    public void updateComment(Long postCommentId, String content) {
        PostComment postComment = postCommentRepository.findById(postCommentId)
                .orElseThrow(() -> new CommentNotFoundException());

        if (postComment.getAuthor() != getMemberFromRequest()) {
            throw new PermissionDeniedException();
        }

        postComment.updateContent(content);
    }

    @Override
    public List<PostCommentInfoDTO> getPostCommentChildren(Long postId, Long commentId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException());
        List<PostCommentInfoDTO> postCommentLists = postCommentRepository.findByPostAndId(post, commentId);
        return postCommentLists;
    }
}
