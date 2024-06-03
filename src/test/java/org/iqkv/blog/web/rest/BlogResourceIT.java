package org.iqkv.blog.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.iqkv.blog.domain.BlogAsserts.*;
import static org.iqkv.blog.web.rest.TestUtil.createUpdateProxyForBean;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.util.IterableUtil;
import org.iqkv.blog.IntegrationTest;
import org.iqkv.blog.domain.Blog;
import org.iqkv.blog.repository.BlogRepository;
import org.iqkv.blog.repository.UserRepository;
import org.iqkv.blog.repository.search.BlogSearchRepository;
import org.iqkv.blog.service.BlogService;
import org.iqkv.blog.service.dto.BlogDTO;
import org.iqkv.blog.service.mapper.BlogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Streamable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link BlogResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class BlogResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_HANDLE = "AAAAAAAAAA";
    private static final String UPDATED_HANDLE = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/blogs";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";
    private static final String ENTITY_SEARCH_API_URL = "/api/blogs/_search";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private UserRepository userRepository;

    @Mock
    private BlogRepository blogRepositoryMock;

    @Autowired
    private BlogMapper blogMapper;

    @Mock
    private BlogService blogServiceMock;

    @Autowired
    private BlogSearchRepository blogSearchRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restBlogMockMvc;

    private Blog blog;

    private Blog insertedBlog;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Blog createEntity(EntityManager em) {
        Blog blog = new Blog().name(DEFAULT_NAME).handle(DEFAULT_HANDLE);
        return blog;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Blog createUpdatedEntity(EntityManager em) {
        Blog blog = new Blog().name(UPDATED_NAME).handle(UPDATED_HANDLE);
        return blog;
    }

    @BeforeEach
    public void initTest() {
        blog = createEntity(em);
    }

    @AfterEach
    public void cleanup() {
        if (insertedBlog != null) {
            blogRepository.delete(insertedBlog);
            blogSearchRepository.delete(insertedBlog);
            insertedBlog = null;
        }
    }

    @Test
    @Transactional
    void createBlog() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(blogSearchRepository.findAll());
        // Create the Blog
        BlogDTO blogDTO = blogMapper.toDto(blog);
        var returnedBlogDTO = om.readValue(
            restBlogMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(blogDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            BlogDTO.class
        );

        // Validate the Blog in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedBlog = blogMapper.toEntity(returnedBlogDTO);
        assertBlogUpdatableFieldsEquals(returnedBlog, getPersistedBlog(returnedBlog));

        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int searchDatabaseSizeAfter = IterableUtil.sizeOf(blogSearchRepository.findAll());
                assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore + 1);
            });

        insertedBlog = returnedBlog;
    }

    @Test
    @Transactional
    void createBlogWithExistingId() throws Exception {
        // Create the Blog with an existing ID
        blog.setId(1L);
        BlogDTO blogDTO = blogMapper.toDto(blog);

        long databaseSizeBeforeCreate = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(blogSearchRepository.findAll());

        // An entity with an existing ID cannot be created, so this API call must fail
        restBlogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(blogDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Blog in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(blogSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    @Transactional
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(blogSearchRepository.findAll());
        // set the field null
        blog.setName(null);

        // Create the Blog, which fails.
        BlogDTO blogDTO = blogMapper.toDto(blog);

        restBlogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(blogDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);

        int searchDatabaseSizeAfter = IterableUtil.sizeOf(blogSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    @Transactional
    void checkHandleIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(blogSearchRepository.findAll());
        // set the field null
        blog.setHandle(null);

        // Create the Blog, which fails.
        BlogDTO blogDTO = blogMapper.toDto(blog);

        restBlogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(blogDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);

        int searchDatabaseSizeAfter = IterableUtil.sizeOf(blogSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    @Transactional
    void getAllBlogs() throws Exception {
        // Initialize the database
        insertedBlog = blogRepository.saveAndFlush(blog);

        // Get all the blogList
        restBlogMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(blog.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].handle").value(hasItem(DEFAULT_HANDLE)));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllBlogsWithEagerRelationshipsIsEnabled() throws Exception {
        when(blogServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restBlogMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(blogServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllBlogsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(blogServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restBlogMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(blogRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getBlog() throws Exception {
        // Initialize the database
        insertedBlog = blogRepository.saveAndFlush(blog);

        // Get the blog
        restBlogMockMvc
            .perform(get(ENTITY_API_URL_ID, blog.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(blog.getId().intValue()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.handle").value(DEFAULT_HANDLE));
    }

    @Test
    @Transactional
    void getNonExistingBlog() throws Exception {
        // Get the blog
        restBlogMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingBlog() throws Exception {
        // Initialize the database
        insertedBlog = blogRepository.saveAndFlush(blog);

        long databaseSizeBeforeUpdate = getRepositoryCount();
        blogSearchRepository.save(blog);
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(blogSearchRepository.findAll());

        // Update the blog
        Blog updatedBlog = blogRepository.findById(blog.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedBlog are not directly saved in db
        em.detach(updatedBlog);
        updatedBlog.name(UPDATED_NAME).handle(UPDATED_HANDLE);
        BlogDTO blogDTO = blogMapper.toDto(updatedBlog);

        restBlogMockMvc
            .perform(put(ENTITY_API_URL_ID, blogDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(blogDTO)))
            .andExpect(status().isOk());

        // Validate the Blog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedBlogToMatchAllProperties(updatedBlog);

        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int searchDatabaseSizeAfter = IterableUtil.sizeOf(blogSearchRepository.findAll());
                assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
                List<Blog> blogSearchList = Streamable.of(blogSearchRepository.findAll()).toList();
                Blog testBlogSearch = blogSearchList.get(searchDatabaseSizeAfter - 1);

                assertBlogAllPropertiesEquals(testBlogSearch, updatedBlog);
            });
    }

    @Test
    @Transactional
    void putNonExistingBlog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(blogSearchRepository.findAll());
        blog.setId(longCount.incrementAndGet());

        // Create the Blog
        BlogDTO blogDTO = blogMapper.toDto(blog);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restBlogMockMvc
            .perform(put(ENTITY_API_URL_ID, blogDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(blogDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Blog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(blogSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    @Transactional
    void putWithIdMismatchBlog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(blogSearchRepository.findAll());
        blog.setId(longCount.incrementAndGet());

        // Create the Blog
        BlogDTO blogDTO = blogMapper.toDto(blog);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBlogMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(blogDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Blog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(blogSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamBlog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(blogSearchRepository.findAll());
        blog.setId(longCount.incrementAndGet());

        // Create the Blog
        BlogDTO blogDTO = blogMapper.toDto(blog);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBlogMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(blogDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Blog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(blogSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    @Transactional
    void partialUpdateBlogWithPatch() throws Exception {
        // Initialize the database
        insertedBlog = blogRepository.saveAndFlush(blog);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the blog using partial update
        Blog partialUpdatedBlog = new Blog();
        partialUpdatedBlog.setId(blog.getId());

        partialUpdatedBlog.handle(UPDATED_HANDLE);

        restBlogMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedBlog.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedBlog))
            )
            .andExpect(status().isOk());

        // Validate the Blog in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertBlogUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedBlog, blog), getPersistedBlog(blog));
    }

    @Test
    @Transactional
    void fullUpdateBlogWithPatch() throws Exception {
        // Initialize the database
        insertedBlog = blogRepository.saveAndFlush(blog);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the blog using partial update
        Blog partialUpdatedBlog = new Blog();
        partialUpdatedBlog.setId(blog.getId());

        partialUpdatedBlog.name(UPDATED_NAME).handle(UPDATED_HANDLE);

        restBlogMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedBlog.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedBlog))
            )
            .andExpect(status().isOk());

        // Validate the Blog in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertBlogUpdatableFieldsEquals(partialUpdatedBlog, getPersistedBlog(partialUpdatedBlog));
    }

    @Test
    @Transactional
    void patchNonExistingBlog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(blogSearchRepository.findAll());
        blog.setId(longCount.incrementAndGet());

        // Create the Blog
        BlogDTO blogDTO = blogMapper.toDto(blog);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restBlogMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, blogDTO.getId()).contentType("application/merge-patch+json").content(om.writeValueAsBytes(blogDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Blog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(blogSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    @Transactional
    void patchWithIdMismatchBlog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(blogSearchRepository.findAll());
        blog.setId(longCount.incrementAndGet());

        // Create the Blog
        BlogDTO blogDTO = blogMapper.toDto(blog);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBlogMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(blogDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Blog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(blogSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamBlog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(blogSearchRepository.findAll());
        blog.setId(longCount.incrementAndGet());

        // Create the Blog
        BlogDTO blogDTO = blogMapper.toDto(blog);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restBlogMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(blogDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Blog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(blogSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    @Transactional
    void deleteBlog() throws Exception {
        // Initialize the database
        insertedBlog = blogRepository.saveAndFlush(blog);
        blogRepository.save(blog);
        blogSearchRepository.save(blog);

        long databaseSizeBeforeDelete = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(blogSearchRepository.findAll());
        assertThat(searchDatabaseSizeBefore).isEqualTo(databaseSizeBeforeDelete);

        // Delete the blog
        restBlogMockMvc
            .perform(delete(ENTITY_API_URL_ID, blog.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(blogSearchRepository.findAll());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore - 1);
    }

    @Test
    @Transactional
    void searchBlog() throws Exception {
        // Initialize the database
        insertedBlog = blogRepository.saveAndFlush(blog);
        blogSearchRepository.save(blog);

        // Search the blog
        restBlogMockMvc
            .perform(get(ENTITY_SEARCH_API_URL + "?query=id:" + blog.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(blog.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].handle").value(hasItem(DEFAULT_HANDLE)));
    }

    protected long getRepositoryCount() {
        return blogRepository.count();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertDecrementedRepositoryCount(long countBefore) {
        assertThat(countBefore - 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected Blog getPersistedBlog(Blog blog) {
        return blogRepository.findById(blog.getId()).orElseThrow();
    }

    protected void assertPersistedBlogToMatchAllProperties(Blog expectedBlog) {
        assertBlogAllPropertiesEquals(expectedBlog, getPersistedBlog(expectedBlog));
    }

    protected void assertPersistedBlogToMatchUpdatableProperties(Blog expectedBlog) {
        assertBlogAllUpdatablePropertiesEquals(expectedBlog, getPersistedBlog(expectedBlog));
    }
}
